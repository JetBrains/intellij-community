// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.execution.filters.ExceptionAnalysisProvider.StackLine;
import com.intellij.ide.actions.ActionsCollector;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class ExceptionWorker {
  @NonNls private static final String AT = "at";
  private static final String AT_PREFIX = AT + " ";
  private static final String STANDALONE_AT = " " + AT + " ";

  private final Project myProject;
  private Filter.Result myResult;
  private ExceptionInfoCache.ClassResolveInfo myClassResolveInfo;
  private String myMethod;
  private ParsedLine myInfo;
  private final ExceptionInfoCache myCache;
  private ExceptionLineRefiner myLocationRefiner;

  public ExceptionWorker(@NotNull ExceptionInfoCache cache) {
    myProject = cache.getProject();
    myCache = cache;
  }

  public Filter.Result execute(@NotNull String line, final int textEndOffset) {
    return execute(line, textEndOffset, null);
  }

  Filter.Result execute(@NotNull String line, final int textEndOffset, @Nullable ExceptionLineRefiner elementMatcher) {
    myResult = null;
    myInfo = parseExceptionLine(line);
    if (myInfo == null || myProject.isDisposed()) {
      return null;
    }

    myMethod = myInfo.methodNameRange.substring(line);

    String className = myInfo.classFqnRange.substring(line).trim();
    myClassResolveInfo = myCache.resolveClassOrFile(className, myInfo.fileName);
    if (myClassResolveInfo.myClasses.isEmpty()) return null;

    /*
     IDEADEV-4976: Some scramblers put something like SourceFile mock instead of real class name.
    final String filePath = fileAndLine.substring(0, colonIndex).replace('/', File.separatorChar);
    final int slashIndex = filePath.lastIndexOf(File.separatorChar);
    final String shortFileName = slashIndex < 0 ? filePath : filePath.substring(slashIndex + 1);
    if (!file.getName().equalsIgnoreCase(shortFileName)) return null;
    */

    final int textStartOffset = textEndOffset - line.length();

    int highlightStartOffset = textStartOffset + myInfo.fileLineRange.getStartOffset();
    int highlightEndOffset = textStartOffset + myInfo.fileLineRange.getEndOffset();

    List<VirtualFile> virtualFiles = new ArrayList<>(myClassResolveInfo.myClasses.keySet());
    HyperlinkInfoFactory.HyperlinkHandler action =
      elementMatcher == null || myInfo.lineNumber <= 0 ? null : new ExceptionColumnFinder(elementMatcher, myInfo.lineNumber - 1, textEndOffset);
    HyperlinkInfo linkInfo = HyperlinkInfoFactory.getInstance().createMultipleFilesHyperlinkInfo(
      virtualFiles, myInfo.lineNumber - 1, myProject, action);
    Filter.Result result = new Filter.Result(highlightStartOffset, highlightEndOffset, linkInfo, myClassResolveInfo.myInLibrary);
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

  ExceptionLineRefiner getLocationRefiner() {
    return myLocationRefiner;
  }

  private static int getLineNumber(@NotNull String lineString) {
    // some quick checks to avoid costly exceptions
    if (lineString.isEmpty() || lineString.length() > 9 || !Character.isDigit(lineString.charAt(0))) {
      return -1;
    }

    try {
      return Integer.parseInt(lineString);
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  @NotNull
  Project getProject() {
    return myProject;
  }
  public Filter.Result getResult() {
    return myResult;
  }

  public PsiClass getPsiClass() {
    return ObjectUtils.tryCast(ContainerUtil.getFirstItem(myClassResolveInfo.myClasses.values()), PsiClass.class);
  }

  public String getMethod() {
    return myMethod;
  }

  public PsiFile getFile() {
    PsiElement element = ContainerUtil.getFirstItem(myClassResolveInfo.myClasses.values());
    return element == null ? null : element.getContainingFile();
  }

  public ParsedLine getInfo() {
    return myInfo;
  }

  private static int findAtPrefix(@NotNull String line) {
    if (line.startsWith(AT_PREFIX)) return 0;

    int startIdx = line.indexOf(STANDALONE_AT);
    return startIdx < 0 ? line.indexOf(AT_PREFIX) : startIdx;
  }

  private static int findRParenAfterLocation(@NotNull String line) {
    int afterDigit = -1;
    int rParenCandidate = line.lastIndexOf(')');
    boolean singleOccurrence = true;
    while (rParenCandidate > 0) {
      if (Character.isDigit(line.charAt(rParenCandidate - 1))) {
        afterDigit = rParenCandidate;
      }
      int prev = line.lastIndexOf(')', rParenCandidate - 1);
      if (prev < 0 && singleOccurrence) {
        return rParenCandidate;
      }
      rParenCandidate = prev;
      singleOccurrence = false;
    }
    return afterDigit;
  }

  @Nullable
  public static ParsedLine parseExceptionLine(@NotNull String line) {
    ParsedLine result = parseNormalStackTraceLine(line);
    if (result == null) result = parseYourKitLine(line);
    if (result == null) result = parseForcedLine(line);
    return result;
  }

  @Nullable
  private static ParsedLine parseNormalStackTraceLine(@NotNull String line) {
    int startIdx = findAtPrefix(line);
    int rParenIdx = findRParenAfterLocation(line);
    if (rParenIdx < 0) return null;

    TextRange methodName = findMethodNameCandidateBefore(line, startIdx, rParenIdx);
    if (methodName == null) return null;

    int lParenIdx = methodName.getEndOffset();
    int dotIdx = methodName.getStartOffset() - 1;
    int moduleIdx = line.indexOf('/');
    int classNameIdx;
    if (moduleIdx > -1 && moduleIdx < dotIdx && !line.startsWith("0x", moduleIdx + 1)) {
      classNameIdx = moduleIdx + 1;
    }
    else {
      if (startIdx >= 0) {
        // consider STANDALONE_AT here
        classNameIdx = startIdx + 1 + AT.length() + (line.charAt(startIdx) == 'a' ? 0 : 1);
      } else {
        classNameIdx = 0;
      }
    }

    return ParsedLine.createFromFileAndLine(new TextRange(classNameIdx, handleSpaces(line, dotIdx, -1)),
                                            trimRange(line, methodName),
                                            lParenIdx + 1, rParenIdx, line);
  }

  @NotNull
  private static TextRange trimRange(@NotNull String line, @NotNull TextRange range) {
    int start = handleSpaces(line, range.getStartOffset(), 1);
    int end = handleSpaces(line, range.getEndOffset(), -1);
    if (start != range.getStartOffset() || end != range.getEndOffset()) {
      return TextRange.create(start, end);
    }
    return range;
  }

  @Nullable
  private static ParsedLine parseYourKitLine(@NotNull String line) {
    int lineEnd = line.length() - 1;
    if (lineEnd > 0 && line.charAt(lineEnd) == '\n') lineEnd--;
    if (lineEnd > 0 && Character.isDigit(line.charAt(lineEnd))) {
      int spaceIndex = line.lastIndexOf(' ');
      int rParenIdx = line.lastIndexOf(')');
      if (rParenIdx > 0 && spaceIndex == rParenIdx + 1) {
        TextRange methodName = findMethodNameCandidateBefore(line, 0, rParenIdx);
        if (methodName != null) {
          return ParsedLine.createFromFileAndLine(new TextRange(0, methodName.getStartOffset() - 1),
                                                  methodName,
                                                  spaceIndex + 1, lineEnd + 1,
                                                  line);
        }
      }
    }
    return null;
  }

  @Nullable
  private static ParsedLine parseForcedLine(@NotNull String line) {
    String dash = "- ";
    if (!line.trim().startsWith(dash)) return null;

    String linePrefix = "line=";
    int lineNumberStart = line.indexOf(linePrefix);
    if (lineNumberStart < 0) return null;

    int lineNumberEnd = line.indexOf(' ', lineNumberStart);
    if (lineNumberEnd < 0) return null;

    TextRange methodName = findMethodNameCandidateBefore(line, 0, lineNumberStart);
    if (methodName == null) return null;

    int lineNumber = getLineNumber(line.substring(lineNumberStart + linePrefix.length(), lineNumberEnd));
    if (lineNumber < 0) return null;

    return new ParsedLine(trimRange(line, TextRange.create(line.indexOf(dash) + dash.length(), methodName.getStartOffset() - 1)),
                          methodName,
                          TextRange.create(lineNumberStart, lineNumberEnd), null, lineNumber);
  }

  private static TextRange findMethodNameCandidateBefore(@NotNull String line, int start, int end) {
    int lParenIdx = line.lastIndexOf('(', end);
    if (lParenIdx < 0) return null;

    int dotIdx = line.lastIndexOf('.', lParenIdx);
    if (dotIdx < 0 || dotIdx < start) return null;

    return TextRange.create(dotIdx + 1, lParenIdx);
  }

  private static int handleSpaces(@NotNull String line, int pos, int delta) {
    int len = line.length();
    while (pos >= 0 && pos < len) {
      final char c = line.charAt(pos);
      if (!Character.isSpaceChar(c)) break;
      pos += delta;
    }
    return pos;
  }

  public static class ParsedLine {
    @NotNull public final TextRange classFqnRange;
    @NotNull public final TextRange methodNameRange;
    @NotNull public final TextRange fileLineRange;
    @Nullable public final String fileName;
    public final int lineNumber;

    ParsedLine(@NotNull TextRange classFqnRange,
               @NotNull TextRange methodNameRange,
               @NotNull TextRange fileLineRange, @Nullable String fileName, int lineNumber) {
      this.classFqnRange = classFqnRange;
      this.methodNameRange = methodNameRange;
      this.fileLineRange = fileLineRange;
      this.fileName = fileName;
      this.lineNumber = lineNumber;
    }

    @Nullable
    private static ParsedLine createFromFileAndLine(@NotNull TextRange classFqnRange,
                                                    @NotNull TextRange methodNameRange,
                                                    int fileLineStart, int fileLineEnd, @NotNull String line) {
      TextRange fileLineRange = TextRange.create(fileLineStart, fileLineEnd);
      String fileAndLine = fileLineRange.substring(line);

      if ("Native Method".equals(fileAndLine) || "Unknown Source".equals(fileAndLine)) {
        return new ParsedLine(classFqnRange, methodNameRange, fileLineRange, null, -1);
      }

      int colonIndex = fileAndLine.lastIndexOf(':');
      if (colonIndex < 0) return null;

      int lineNumber = getLineNumber(fileAndLine.substring(colonIndex + 1));
      if (lineNumber < 0) return null;

      return new ParsedLine(classFqnRange, methodNameRange, fileLineRange, fileAndLine.substring(0, colonIndex).trim(), lineNumber);
    }
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
    public void onLinkFollowed(@NotNull PsiFile file, @NotNull Editor targetEditor, @Nullable Editor originalEditor) {
      Project project = file.getProject();
      if (DumbService.isDumb(project)) return; // may need to resolve refs
      Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
      if (document == null || document.getLineCount() <= myLineNumber) return;
      if (!PsiDocumentManager.getInstance(project).isCommitted(document)) return;
      int startOffset = document.getLineStartOffset(myLineNumber);
      int endOffset = document.getLineEndOffset(myLineNumber);
      LinkInfo info = ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(() -> ReadAction.compute(() -> computeLinkInfo(file, startOffset, endOffset, originalEditor)),
                                             JavaBundle.message("exception.navigation.fetching.target.position"), true, project);
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

    private @Nullable LinkInfo computeLinkInfo(@NotNull PsiFile file, int lineStart, int lineEnd, @Nullable Editor originalEditor) {
      PsiElement target = getExceptionOrigin(file, lineStart, lineEnd);
      if (target == null) return null;
      AnAction action = findAnalysisAction(file.getProject(), target, originalEditor);
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
      Balloon balloon = JBPopupFactory.getInstance()
        .createHtmlTextBalloonBuilder(HtmlChunk.link("analyze", actionName).toString(), null, 
                                      MessageType.INFO.getPopupBackground(), new HyperlinkAdapter() {
            @Override
            protected void hyperlinkActivated(HyperlinkEvent e) {
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
        .setDisposable(project)
        .createBalloon();
      ref.set(balloon);
      RelativePoint point = JBPopupFactory.getInstance().guessBestPopupLocation(editor);
      balloon.show(point, Balloon.Position.below);
      editor.getScrollingModel().addVisibleAreaListener(e -> {
        Disposer.dispose(balloon);
      }, balloon);
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
