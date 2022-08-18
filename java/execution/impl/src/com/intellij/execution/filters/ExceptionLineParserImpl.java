// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.execution.filters.ExceptionAnalysisProvider.StackLine;
import com.intellij.ide.actions.ActionsCollector;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.intellij.execution.filters.ExceptionWorker.ParsedLine;
import static com.intellij.execution.filters.ExceptionWorker.parseExceptionLine;

public class ExceptionLineParserImpl implements ExceptionLineParser {
  private final Project myProject;
  private Filter.Result myResult;
  private ExceptionInfoCache.ClassResolveInfo myClassResolveInfo;
  private String myMethod;
  private ParsedLine myInfo;
  private final ExceptionInfoCache myCache;
  private ExceptionLineRefiner myLocationRefiner;

  public ExceptionLineParserImpl(@NotNull ExceptionInfoCache cache) {
    myProject = cache.getProject();
    myCache = cache;
  }

  @Override
  public Filter.Result execute(@NotNull String line, final int textEndOffset) {
    return execute(line, textEndOffset, null);
  }

  @Override
  public Filter.Result execute(@NotNull String line, final int textEndOffset, @Nullable ExceptionLineRefiner elementMatcher) {
    myResult = null;
    myInfo = parseExceptionLine(line);
    if (myInfo == null || myProject.isDisposed()) {
      return null;
    }

    myMethod = myInfo.methodNameRange.substring(line);

    String className = myInfo.classFqnRange.substring(line).trim();
    myClassResolveInfo = myCache.resolveClassOrFile(className, myInfo.fileName);
    if (myClassResolveInfo.getClasses().isEmpty()) return null;

    final int textStartOffset = textEndOffset - line.length();

    int highlightStartOffset = textStartOffset + myInfo.fileLineRange.getStartOffset();
    int highlightEndOffset = textStartOffset + myInfo.fileLineRange.getEndOffset();

    List<VirtualFile> virtualFiles = new ArrayList<>(myClassResolveInfo.getClasses().keySet());
    HyperlinkInfoFactory.HyperlinkHandler action =
      elementMatcher == null || myInfo.lineNumber <= 0 ? null : new ExceptionColumnFinder(elementMatcher, myInfo.lineNumber - 1, textEndOffset);
    HyperlinkInfo linkInfo = HyperlinkInfoFactory.getInstance().createMultipleFilesHyperlinkInfo(
      virtualFiles, myInfo.lineNumber - 1, myProject, action);
    Filter.Result result = new Filter.Result(highlightStartOffset, highlightEndOffset, linkInfo, myClassResolveInfo.isInLibrary());
    if (myMethod.startsWith("access$")) {
      // Bridge method: just skip it
      myLocationRefiner = elementMatcher;
    }
    else if (elementMatcher instanceof FunctionCallMatcher && className.matches(".+\\$\\$Lambda\\$\\d+/0x[0-9a-f]+")) {
      // Like at com.example.MyClass$$Lambda$3363/0x00000008026d6440.fun(Unknown Source)
      myLocationRefiner = new FunctionCallMatcher(myMethod);
    }
    else if (myMethod.startsWith("lambda$")) {
      myLocationRefiner = new FunctionCallMatcher(null);
    }
    else {
      myLocationRefiner = new StackFrameMatcher(line, myInfo);
    }
    myResult = result;
    return result;
  }

  @Override
  public ExceptionLineRefiner getLocationRefiner() {
    return myLocationRefiner;
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  public Filter.Result getResult() {
    return myResult;
  }

  @Override
  public PsiClass getPsiClass() {
    return ObjectUtils.tryCast(ContainerUtil.getFirstItem(myClassResolveInfo.getClasses().values()), PsiClass.class);
  }

  @Override
  public String getMethod() {
    return myMethod;
  }

  @Override
  public PsiFile getFile() {
    PsiElement element = ContainerUtil.getFirstItem(myClassResolveInfo.getClasses().values());
    return element == null ? null : element.getContainingFile();
  }

  @Override
  public ParsedLine getInfo() {
    return myInfo;
  }

  private static final class StackFrameMatcher implements ExceptionLineRefiner {
    private final @NonNls String myMethodName;
    private final @NonNls String myClassName;
    private final boolean myHasDollarInName;

    private StackFrameMatcher(@NotNull String line, @NotNull ParsedLine info) {
      myMethodName = info.methodNameRange.substring(line);
      myClassName = info.classFqnRange.substring(line);
      myHasDollarInName = StringUtil.getShortName(myClassName).contains("$");
    }

    @Override
    public PsiElement matchElement(@NotNull PsiElement element) {
      if (myMethodName.equals("requireNonNull") && myClassName.equals(CommonClassNames.JAVA_UTIL_OBJECTS)) {
        // Since Java 9 Objects.requireNonNull(x) is used by javac instead of x.getClass() for generated null-check (JDK-8074306)
        PsiExpression expression = NullPointerExceptionInfo.matchCompilerGeneratedNullCheck(element);
        if (expression != null) {
          return expression;
        }
      }
      if (!(element instanceof PsiIdentifier)) return null;
      if (myMethodName.equals("<init>")) {
        if (myHasDollarInName || element.textMatches(StringUtil.getShortName(myClassName))) {
          PsiElement parent = element.getParent();
          while (parent instanceof PsiJavaCodeReferenceElement) {
            parent = parent.getParent();
          }
          if (parent instanceof PsiAnonymousClass) {
            return isTargetClass(parent) || isTargetClass(((PsiAnonymousClass)parent).getSuperClass()) ? element : null;
          }
          if (parent instanceof PsiNewExpression) {
            PsiJavaCodeReferenceElement ref = ((PsiNewExpression)parent).getClassOrAnonymousClassReference();
            return ref != null && isTargetClass(ref.resolve()) ? element : null;
          }
        }
      }
      else if (element.textMatches(myMethodName)) {
        PsiElement parent = element.getParent();
        if (parent instanceof PsiReferenceExpression) {
          PsiElement target = ((PsiReferenceExpression)parent).resolve();
          return target instanceof PsiMethod && isTargetClass(((PsiMethod)target).getContainingClass()) ? element : null;
        }
      }
      return null;
    }

    private boolean isTargetClass(PsiElement maybeClass) {
      if (!(maybeClass instanceof PsiClass)) return false;
      if (myClassName.startsWith("com.sun.proxy.$Proxy")) return true;
      PsiClass declaredClass = (PsiClass)maybeClass;
      String declaredName = declaredClass.getQualifiedName();
      if (myClassName.equals(declaredName)) return true;
      PsiClass calledClass = ClassUtil.findPsiClass(maybeClass.getManager(), myClassName, declaredClass, true);
      if (calledClass == null) {
        calledClass = ClassUtil.findPsiClass(maybeClass.getManager(), myClassName, null, true);
      }
      return calledClass == declaredClass || declaredName != null && InheritanceUtil.isInheritor(calledClass, false, declaredName);
    }
  }

  private static final class ExceptionColumnFinder implements HyperlinkInfoFactory.HyperlinkHandler {
    private static final long LINK_INFO_TIMEOUT_MS = 300L;
    private final ExceptionLineRefiner myElementMatcher;
    private final int myLineNumber;
    private final int myTextEndOffset;
    private boolean myAnalysisWasActivated;

    private ExceptionColumnFinder(@NotNull ExceptionLineRefiner elementMatcher, int lineNumber, int textEndOffset) {
      myElementMatcher = elementMatcher;
      myLineNumber = lineNumber;
      myTextEndOffset = textEndOffset;
    }

    @Override
    public void onLinkFollowed(@NotNull Project project,
                               @NotNull VirtualFile file,
                               @NotNull Editor targetEditor,
                               @Nullable Editor originalEditor) {
      if (DumbService.isDumb(project)) return; // may need to resolve refs
      Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document == null || document.getLineCount() <= myLineNumber) return;
      if (!PsiDocumentManager.getInstance(project).isCommitted(document)) return;
      int startOffset = document.getLineStartOffset(myLineNumber);
      int endOffset = document.getLineEndOffset(myLineNumber);

      ThrowableComputable<LinkInfo, RuntimeException> computable = () -> ReadAction.compute(
        () -> computeLinkInfo(project, file, startOffset, endOffset, originalEditor));
      LinkInfo info = ProgressIndicatorUtils.withTimeout(LINK_INFO_TIMEOUT_MS, () -> SlowOperations.allowSlowOperations(computable));
      if (info == null) return;
      TextRange range = info.target.getTextRange();
      targetEditor.getCaretModel().moveToOffset(range.getStartOffset());
      if (info.analysisAction != null) {
        displayAnalysisAction(project, info.target, targetEditor, info.analysisAction);
      }
    }

    private static class LinkInfo {
      final @NotNull PsiElement target;
      final @Nullable AnAction analysisAction;

      private LinkInfo(@NotNull PsiElement target, @Nullable AnAction action) {
        this.target = target;
        analysisAction = action;
      }
    }

    private @Nullable LinkInfo computeLinkInfo(@NotNull Project project,
                                               @NotNull VirtualFile file,
                                               int lineStart,
                                               int lineEnd,
                                               @Nullable Editor originalEditor) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile == null) return null;
      PsiElement target = getExceptionOrigin(psiFile, lineStart, lineEnd);
      if (target == null) return null;
      AnAction action = findAnalysisAction(project, target, originalEditor);
      return new LinkInfo(target, action);
    }

    private @Nullable PsiElement getExceptionOrigin(@NotNull PsiFile file, int lineStart, int lineEnd) {
      if (!file.isValid()) return null;
      PsiElement element = file.findElementAt(lineStart);
      List<PsiElement> candidates = new ArrayList<>();
      while (element != null && element.getTextRange().getStartOffset() < lineEnd) {
        PsiElement finalElement = element;
        PsiElement matched = myElementMatcher.matchElement(finalElement);
        if (matched != null) {
          candidates.add(matched);
          if (candidates.size() > 1) return null;
        }
        element = PsiTreeUtil.nextLeaf(element);
      }
      return ContainerUtil.getOnlyItem(candidates);
    }

    private void displayAnalysisAction(@NotNull Project project, @NotNull PsiElement element, @NotNull Editor editor, AnAction action) {
      String actionName = action.getTemplatePresentation().getDescription();
      Objects.requireNonNull(actionName);
      Ref<Balloon> ref = Ref.create();
      String content = HtmlChunk.link("analyze", actionName).toString();
      Color background = MessageType.INFO.getPopupBackground();
      Balloon balloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(content, null, background, new HyperlinkAdapter() {
          @Override
          protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
            if (e.getDescription().equals("analyze")) {
              Balloon b = ref.get();
              if (b != null) {
                Disposer.dispose(b);
              }
              myAnalysisWasActivated = true;
              ActionsCollector.getInstance().record(project, action, null, element.getLanguage());
              action.actionPerformed(AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, DataContext.EMPTY_CONTEXT));
            }
          }
        })
        .createBalloon();
      EditorUtil.disposeWithEditor(editor, balloon);
      ref.set(balloon);
      RelativePoint point = JBPopupFactory.getInstance().guessBestPopupLocation(editor);
      balloon.show(point, Balloon.Position.below);
      editor.getScrollingModel().addVisibleAreaListener(e -> Disposer.dispose(balloon), balloon);
    }

    @Nullable
    private AnAction findAnalysisAction(@NotNull Project project, @NotNull PsiElement element, @Nullable Editor originalEditor) {
      if (myAnalysisWasActivated) {
        // Do not show the balloon if analysis was already activated once on this link
        return null;
      }
      Supplier<List<StackLine>> supplier;
      if (originalEditor != null) {
        Document origDocument = originalEditor.getDocument();
        supplier = () -> {
          int stackLineNumber = origDocument.getLineNumber(myTextEndOffset);
          if (stackLineNumber < 1) return Collections.emptyList();
          int lineCount = Math.min(origDocument.getLineCount(), stackLineNumber + 100);
          List<StackLine> nextLines = new ArrayList<>();
          for (int i = stackLineNumber - 1; i < lineCount; i++) {
            String traceLine = origDocument.getText(TextRange.create(origDocument.getLineStartOffset(i), origDocument.getLineEndOffset(i)));
            ParsedLine line = parseExceptionLine(traceLine);
            if (line == null) break;
            String methodName = line.methodNameRange.substring(traceLine);
            if (methodName.startsWith("access$")) continue;
            StackLine stackLine = new StackLine(line.classFqnRange.substring(traceLine), methodName, line.fileName);
            nextLines.add(stackLine);
          }
          return nextLines;
        };
      } else {
        supplier = Collections::emptyList;
      }
      ExceptionInfo info = myElementMatcher.getExceptionInfo();
      ExceptionAnalysisProvider exceptionAnalysisProvider = project.getService(ExceptionAnalysisProvider.class);
      AnAction action;
      if (info == null) {
        action = exceptionAnalysisProvider.getIntermediateRowAnalysisAction(element, supplier);
      } else {
        action = exceptionAnalysisProvider.getAnalysisAction(element, info, supplier);
      }
      return action;
    }
  }

  private static class FunctionCallMatcher implements ExceptionLineRefiner {
    private final @Nullable String myMethodName;

    private FunctionCallMatcher(@Nullable String name) {
      myMethodName = name;
    }

    @Override
    public PsiElement matchElement(@NotNull PsiElement element) {
      if (!(element instanceof PsiIdentifier)) return null;
      if (myMethodName != null && !element.textMatches(myMethodName)) return null;
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiReferenceExpression)) return null;
      PsiMethodCallExpression call = ObjectUtils.tryCast(parent.getParent(), PsiMethodCallExpression.class);
      if (call == null) return null;
      PsiMethod target = call.resolveMethod();
      if (target == null) return null;
      if (LambdaUtil.getFunctionalInterfaceMethod(target.getContainingClass()) != target) return null;
      return element;
    }
  }
}
