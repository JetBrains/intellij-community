// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.daemon.quickFix.CreateFilePathFix;
import com.intellij.codeInsight.daemon.quickFix.NewFileLocation;
import com.intellij.codeInsight.daemon.quickFix.TargetDirectory;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.*;
import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PsiFileReference;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class JavaDocReferenceInspection extends LocalInspectionTool {
  private static final String SHORT_NAME = "JavadocReference";

  @SuppressWarnings("WeakerAccess")
  public boolean REPORT_INACCESSIBLE = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("REPORT_INACCESSIBLE", JavaBundle.message("checkbox.html.report.inaccessible.symbols")));
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.javadoc.issues");
  }

  @Override
  public @NotNull String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public @NotNull HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitJavaFile(@NotNull PsiJavaFile file) {
        if (PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
          checkComment(PsiTreeUtil.getChildOfType(file, PsiDocComment.class), file, holder, isOnTheFly);
        }
      }

      @Override
      public void visitModule(@NotNull PsiJavaModule module) {
        checkComment(module.getDocComment(), module, holder, isOnTheFly);
      }

      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        checkComment(aClass.getDocComment(), aClass, holder, isOnTheFly);
      }

      @Override
      public void visitField(@NotNull PsiField field) {
        checkComment(field.getDocComment(), field, holder, isOnTheFly);
      }

      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        checkComment(method.getDocComment(), method, holder, isOnTheFly);
      }
    };
  }

  private void checkComment(@Nullable PsiDocComment comment, PsiElement context, ProblemsHolder holder, boolean isOnTheFly) {
    if (comment == null) return;

    JavadocManager javadocManager = JavadocManager.getInstance(holder.getProject());
    comment.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
        visitRefElement(reference, context, isOnTheFly, holder);
      }

      @Override
      public void visitSnippetAttributeValue(@NotNull PsiSnippetAttributeValue attributeValue) {
        PsiReference ref = attributeValue.getReference();
        if (ref instanceof PsiFileReference fileRef) {
          PsiElement resolved = fileRef.resolve();
          if (resolved == null) {
            CreateFilePathFix fix = null;
            String path = fileRef.getCanonicalText();
            PsiDirectory parent = comment.getContainingFile().getParent();
            if (parent != null) {
              String[] components = path.split("/");
              if (components.length > 0) {
                TargetDirectory directory = new TargetDirectory(parent, Arrays.copyOf(components, components.length - 1));
                NewFileLocation location = new NewFileLocation(Collections.singletonList(directory), components[components.length - 1]);
                fix = new CreateFilePathFix(attributeValue, location);
              }
            }
            holder.registerProblem(attributeValue, 
                                   JavaBundle.message("inspection.message.snippet.file.not.found", path),
                                   LocalQuickFix.notNullElements(fix));
          }
        }
        PsiSymbolReference symRef = ContainerUtil.getOnlyItem(attributeValue.getOwnReferences());
        if (symRef != null) {
          Collection<? extends Symbol> target = symRef.resolveReference();
          if (target.isEmpty()) {
            holder.registerProblem(attributeValue, JavaBundle.message("inspection.message.snippet.region.not.found"));
          }
        }
      }

      @Override
      public void visitDocTag(@NotNull PsiDocTag tag) {
        super.visitDocTag(tag);
        visitRefInDocTag(tag, javadocManager, context, holder, isOnTheFly);
      }
    });
  }

  private void visitRefElement(PsiJavaCodeReferenceElement reference, PsiElement context, boolean isOnTheFly, ProblemsHolder holder) {
    JavaResolveResult result = reference.advancedResolve(false);
    String refText = reference.getText();
    String message = getResolveErrorMessage(result.getElement(), context, refText);
    if (message != null && !result.isPackagePrefixPackageReference()) {
      PsiElement element = Objects.requireNonNullElse(reference.getReferenceNameElement(), reference);

      LocalQuickFix fix = null;
      if (isOnTheFly) {
        List<? extends PsiClass> classesToImport = new ImportClassFix(reference).getClassesToImport();
        if (!classesToImport.isEmpty()) {
          fix = new AddQualifierFix(classesToImport);
        }
        else if (URLUtil.HTTP_PROTOCOL.equals(refText) || URLUtil.HTTPS_PROTOCOL.equals(refText)) {
          PsiElement refHolder = reference.getParent();
          if (refHolder != null && refHolder.getNode().getElementType() == JavaDocElementType.DOC_REFERENCE_HOLDER) {
            PsiElement adjacent = refHolder.getNextSibling();
            if (adjacent instanceof PsiDocToken &&
                ((PsiDocToken)adjacent).getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA &&
                adjacent.getText().startsWith(URLUtil.SCHEME_SEPARATOR)) {
              PsiDocComment docComment = PsiTreeUtil.getParentOfType(reference, PsiDocComment.class);
              if (docComment != null) {
                int startOffsetInDocComment = refHolder.getTextOffset() - docComment.getTextOffset();
                int endOffsetInDocComment =
                  refHolder.getTextOffset() + refText.length() + adjacent.getTextLength() - docComment.getTextOffset();
                fix = LocalQuickFix.from(new UrlToHtmlFix(docComment, startOffsetInDocComment, endOffsetInDocComment));
              }
            }
          }
        }
      }

      holder.registerProblem(holder.getManager().createProblemDescriptor(
        element, message, fix, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, isOnTheFly));
    }
  }

  private void visitRefInDocTag(PsiDocTag tag, JavadocManager manager, PsiElement context, ProblemsHolder holder, boolean isOnTheFly) {
    PsiDocTagValue value = tag.getValueElement();
    if (value == null) return;

    String tagName = tag.getName();
    JavadocTagInfo info = manager.getTagInfo(tagName);
    if (info != null && !info.isValidInContext(context)) return;

    if (info != null && info.isInline()) {
      String message = info.checkTagValue(value);
      if (message != null) {
        holder.registerProblem(holder.getManager().createProblemDescriptor(
          value, message, isOnTheFly, null, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL));
      }
    }

    PsiReference reference = value.getReference();
    if (reference == null) return;
    int textOffset = value.getTextOffset();
    if (textOffset == value.getTextRange().getEndOffset()) return;
    PsiDocTagValue valueElement = tag.getValueElement();
    if (valueElement == null) return;

    PsiElement element = reference.resolve();
    String paramName =
      value.getContainingFile().getViewProvider().getContents().subSequence(textOffset, value.getTextRange().getEndOffset()).toString();
    String message = element == null && reference instanceof PsiPolyVariantReference ?
                     getResolveErrorMessage(((PsiPolyVariantReference)reference).multiResolve(false), context, paramName) :
                     getResolveErrorMessage(element, context, paramName);
    if (message == null) {
      return;
    }

    List<LocalQuickFix> fixes = new ArrayList<>();

    if (isOnTheFly && "param".equals(tagName)) {
      PsiDocCommentOwner commentOwner = PsiTreeUtil.getParentOfType(tag, PsiDocCommentOwner.class);
      if (commentOwner instanceof PsiMethod method) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        PsiDocTag[] tags = tag.getContainingComment().getTags();
        Set<String> unboundParams = new HashSet<>();
        for (PsiParameter parameter : parameters) {
          if (!MissingJavadocInspection.hasTagForParameter(tags, parameter)) {
            unboundParams.add(parameter.getName());
          }
        }
        if (!unboundParams.isEmpty()) {
          fixes.add(new RenameReferenceQuickFix(unboundParams));
        }
      }
    }

    fixes.add(new RemoveTagFix(tagName, paramName));

    if (isOnTheFly && element != null && REPORT_INACCESSIBLE) {
      fixes.add(LocalQuickFix.from(new UpdateInspectionOptionFix(
        this, "REPORT_INACCESSIBLE", JavaBundle.message("disable.report.inaccessible.symbols.fix"), false)));
    }

    holder.registerProblem(holder.getManager().createProblemDescriptor(
      valueElement, reference.getRangeInElement(), message, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, isOnTheFly,
      fixes.toArray(LocalQuickFix.EMPTY_ARRAY)));
  }

  private @InspectionMessage String getResolveErrorMessage(ResolveResult[] resolveResults, PsiElement context, CharSequence referenceText) {
    if (resolveResults.length == 0) {
      return JavaBundle.message("inspection.javadoc.problem.cannot.resolve", "<code>" + referenceText + "</code>");
    }

    if (REPORT_INACCESSIBLE &&
        !Arrays.stream(resolveResults).map(ResolveResult::getElement).filter(Objects::nonNull).allMatch(e -> isAccessible(e, context))) {
      return JavaBundle.message("inspection.javadoc.problem.inaccessible", "<code>" + referenceText + "</code>");
    }

    return null;
  }

  private @InspectionMessage String getResolveErrorMessage(@Nullable PsiElement resolved, PsiElement context, CharSequence referenceText) {
    if (resolved == null) {
      return JavaBundle.message("inspection.javadoc.problem.cannot.resolve", "<code>" + referenceText + "</code>");
    }

    if (REPORT_INACCESSIBLE && !isAccessible(resolved, context)) {
      return JavaBundle.message("inspection.javadoc.problem.inaccessible", "<code>" + referenceText + "</code>");
    }

    return null;
  }

  private static boolean isAccessible(PsiElement resolved, PsiElement context) {
    if (!(resolved instanceof PsiMember)) {
      return true;
    }
    if (!PsiResolveHelper.getInstance(resolved.getProject()).isAccessible((PsiMember)resolved, context, null)) {
      return false;
    }
    VirtualFile file = PsiUtilCore.getVirtualFile(resolved);
    return file == null || context.getResolveScope().contains(file);
  }

  private static class RenameReferenceQuickFix extends PsiUpdateModCommandQuickFix {
    private final Set<String> myUnboundParams;

    RenameReferenceQuickFix(Set<String> unboundParams) {
      myUnboundParams = unboundParams;
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("quickfix.family.change.javadoc.to");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      String first = myUnboundParams.iterator().next();
      updater.templateBuilder().field(element, new ConstantNode(first).withLookupStrings(myUnboundParams));
    }
  }

  private static class AddQualifierFix extends ModCommandQuickFix {
    private final List<? extends SmartPsiElementPointer<PsiClass>> originalClasses;

    AddQualifierFix(List<? extends PsiClass> originalClasses) {
      this.originalClasses = ContainerUtil.map(originalClasses, SmartPointerManager::createPointer);
    }

    @Override
    public @NotNull String getFamilyName() {
      return QuickFixBundle.message("add.qualifier");
    }

    @Override
    public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiJavaCodeReferenceElement ref = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiJavaCodeReferenceElement.class);
      if (ref == null) return ModCommand.nop();
      List<ModCommandAction> actions = StreamEx.of(originalClasses).map(SmartPsiElementPointer::getElement)
        .nonNull().sorted(new PsiProximityComparator(ref.getElement()))
        .<ModCommandAction>map(cls -> new BindClassAction(ref, cls))
        .toList();
      return ModCommand.chooseAction(QuickFixBundle.message("add.qualifier.original.class.chooser.title"), actions);
    }
  }
  
  private static final class BindClassAction extends PsiUpdateModCommandAction<PsiJavaCodeReferenceElement> {
    private final SmartPsiElementPointer<PsiClass> myClass;
    
    BindClassAction(@NotNull PsiJavaCodeReferenceElement ref, @NotNull PsiClass psiClass) {
      super(ref);
      myClass = SmartPointerManager.createPointer(psiClass);
    }
    
    @Override
    public @NotNull String getFamilyName() {
      return "";
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiJavaCodeReferenceElement element) {
      PsiClass cls = myClass.getElement();
      if (cls == null) return null;
      String name = cls.getQualifiedName();
      if (name == null) return null;
      return Presentation.of(name).withIcon(cls.getIcon(0));
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiJavaCodeReferenceElement ref, @NotNull ModPsiUpdater updater) {
      PsiClass element = myClass.getElement();
      if (element != null) {
        ref.bindToElement(element);
      }
    }
  }

  private static class RemoveTagFix extends PsiUpdateModCommandQuickFix {
    private final String myTagName;
    private final String myParamName;

    RemoveTagFix(String tagName, String paramName) {
      myTagName = tagName;
      myParamName = paramName;
    }

    @Override
    public @NotNull String getName() {
      return JavaBundle.message("quickfix.text.remove.javadoc.0.1", myTagName, myParamName);
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("quickfix.family.remove.javadoc.tag");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiDocTag myTag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
      if (myTag != null) {
        myTag.delete();
      }
    }
  }
}
