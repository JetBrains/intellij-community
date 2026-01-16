// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.completion.AllClassesGetter;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.PsiUpdateCompletionItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.siyeh.ig.psiutils.JavaDeprecationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

@NotNullByDefault
public final class ClassReferenceCompletionItem extends PsiUpdateCompletionItem<PsiClass> {
  private final @Nullable String myQualifiedName;
  private final @Nullable String myForcedPresentableName;
  @NlsSafe private final String myPackageDisplayName;
  private final boolean myStrikeout;
  private final PsiSubstitutor mySubstitutor;
  
  private ClassReferenceCompletionItem(PsiClass psiClass, PsiSubstitutor substitutor, @Nullable String forcedPresentableName) {
    super(Objects.requireNonNullElseGet(forcedPresentableName, () -> Objects.requireNonNull(psiClass.getName())), psiClass);
    myQualifiedName = psiClass.getQualifiedName();
    myForcedPresentableName = forcedPresentableName;
    myPackageDisplayName = PsiFormatUtil.getPackageDisplayName(psiClass);
    myStrikeout = JavaDeprecationUtils.isDeprecated(psiClass, null);
    mySubstitutor = substitutor;
  }
  
  public ClassReferenceCompletionItem(PsiClass psiClass) {
    this(psiClass, PsiSubstitutor.EMPTY, null);
  }

  @Override
  public Set<@NlsSafe String> additionalLookupStrings() {
    return myForcedPresentableName == null ? Set.of() : Set.of(myForcedPresentableName);
  }

  public ClassReferenceCompletionItem withPresentableName(@Nullable String forcedPresentableName) {
    return new ClassReferenceCompletionItem(contextObject(), mySubstitutor, forcedPresentableName);
  }
  
  public ClassReferenceCompletionItem withSubstitutor(PsiSubstitutor substitutor) {
    return new ClassReferenceCompletionItem(contextObject(), substitutor, myForcedPresentableName);
  }

  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

  @Override
  public void update(ActionContext actionContext, InsertionContext insertionContext, ModPsiUpdater updater) {
    if (!contextObject().isValid()) return;
    PsiDocumentManager.getInstance(updater.getProject()).commitDocument(updater.getDocument());
    if (processInImport(updater)) return;
    if (processJavaDoc(actionContext.offset(), updater)) return;
    AllClassesGetter.tryShorten(updater.getPsiFile(), updater, contextObject());
  }

  private boolean processJavaDoc(int startOffset, ModPsiUpdater updater) {
    PsiFile file = updater.getPsiFile();
    int offset = updater.getCaretOffset();
    PsiElement position = file.findElementAt(offset - 1);
    PsiJavaCodeReferenceElement ref = position != null && position.getParent() instanceof PsiJavaCodeReferenceElement codeRef ?
                                      codeRef : null;
    String qname = myQualifiedName;
    if (qname != null && PsiTreeUtil.getParentOfType(position, PsiDocComment.class, false) != null &&
        (ref == null || !ref.isQualified()) &&
        shouldInsertFqnInJavadoc(file)) {
      updater.getDocument().replaceString(startOffset, offset, qname);
      return true;
    }

    return ref != null && PsiTreeUtil.getParentOfType(position, PsiDocTag.class) != null && ref.isReferenceTo(contextObject());
  }

  private boolean shouldInsertFqnInJavadoc(PsiFile file) {
    return switch (JavaCodeStyleSettings.getInstance(file).CLASS_NAMES_IN_JAVADOC) {
      case JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS -> true;
      case JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED -> file instanceof PsiJavaFile javaFile && myQualifiedName != null &&
                                                                        !ImportHelper.isAlreadyImported(javaFile, myQualifiedName);
      default -> false;
    };
  }

  private boolean processInImport(ModPsiUpdater updater) {
    int offset = updater.getCaretOffset();
    PsiFile file = updater.getPsiFile();
    PsiImportStatementBase importStatement = PsiTreeUtil.findElementOfClassAtOffset(file, offset - 1, PsiImportStatementBase.class, false);
    if (importStatement == null) return false;
    PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(file, offset - 1, PsiJavaCodeReferenceElement.class, false);
    Document document = updater.getDocument();
    if (myQualifiedName != null && (ref == null || !myQualifiedName.equals(ref.getCanonicalText()))) {
      int start = JavaCompletionUtil.findQualifiedNameStart(offset, document);
      document.replaceString(start, offset, myQualifiedName);
    }
    if (importStatement instanceof PsiImportStaticStatement) {
      //context.setAddCompletionChar(false);
      document.insertString(offset, ".");
      updater.moveCaretTo(offset + 1);
    }
    return true;
  }

  @Override
  public ModCompletionItemPresentation presentation() {
    String name = getName();
    String tailText = " " + myPackageDisplayName;
    PsiClass psiClass = contextObject();
    if (mySubstitutor == PsiSubstitutor.EMPTY && psiClass.getTypeParameters().length > 0) {
      String separator = "," + (showSpaceAfterComma(psiClass) ? " " : "");
      tailText = "<" + StringUtil.join(psiClass.getTypeParameters(), PsiTypeParameter::getName, separator) + ">" + tailText;
    }
    MarkupText mainText = MarkupText.builder()
      .append(name, myStrikeout ? MarkupText.Kind.STRIKEOUT : MarkupText.Kind.NORMAL)
      .append(tailText, MarkupText.Kind.GRAYED).build();
    return new ModCompletionItemPresentation(mainText)
      .withMainIcon(() -> psiClass.getIcon(Registry.is("ide.completion.show.visibility.icon") ? Iconable.ICON_FLAG_VISIBILITY : 0));
  }

  @NlsSafe
  private String getName() {
    if (myForcedPresentableName != null) {
      return myForcedPresentableName;
    }

    PsiClass psiClass = contextObject();
    String name = PsiUtilCore.getName(psiClass);

    if (mySubstitutor != PsiSubstitutor.EMPTY) {
      final PsiTypeParameter[] params = psiClass.getTypeParameters();
      if (params.length > 0) {
        return name + formatTypeParameters(mySubstitutor, params);
      }
    }

    return StringUtil.notNullize(name);
  }

  @Nullable String getForcedPresentableName() {
    return myForcedPresentableName;
  }

  private static String formatTypeParameters(final PsiSubstitutor substitutor, final PsiTypeParameter[] params) {
    final boolean space = showSpaceAfterComma(params[0]);
    StringBuilder buffer = new StringBuilder();
    buffer.append("<");
    for(int i = 0; i < params.length; i++){
      final PsiTypeParameter param = params[i];
      final PsiType type = substitutor.substitute(param);
      if(type == null){
        return "";
      }
      if (type instanceof PsiClassType classType && classType.getParameters().length > 0) {
        buffer.append(classType.rawType().getPresentableText()).append("<...>");
      } else {
        buffer.append(type.getPresentableText());
      }

      if(i < params.length - 1) {
        buffer.append(",");
        if (space) {
          buffer.append(" ");
        }
      }
    }
    buffer.append(">");
    return buffer.toString();
  }

  private static boolean showSpaceAfterComma(PsiClass element) {
    return CodeStyle.getLanguageSettings(element.getContainingFile(), JavaLanguage.INSTANCE).SPACE_AFTER_COMMA;
  }

  @Override
  public boolean equals(@Nullable final Object o) {
    if (this == o) return true;
    if (!(o instanceof ClassReferenceCompletionItem that)) return false;
    if (myQualifiedName != null) {
      return myQualifiedName.equals(that.myQualifiedName);
    }
    return Comparing.equal(contextObject(), that.contextObject());
  }

  public @Nullable String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public int hashCode() {
    final String s = myQualifiedName;
    return s == null ? 239 : s.hashCode();
  }

}
