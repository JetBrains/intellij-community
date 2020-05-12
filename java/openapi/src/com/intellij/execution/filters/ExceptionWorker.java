/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.filters;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

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

    myClassResolveInfo = myCache.resolveClass(myInfo.classFqnRange.substring(line).trim());
    if (myClassResolveInfo.myClasses.isEmpty() && myInfo.fileName != null) {
      // try find the file with the required name
      //todo[nik] it would be better to use FilenameIndex here to honor the scope by it isn't accessible in Open API
      PsiFile[] files = PsiShortNamesCache.getInstance(myProject).getFilesByName(myInfo.fileName);
      myClassResolveInfo = ExceptionInfoCache.ClassResolveInfo.create(myProject, files);
    }
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
    ToIntFunction<PsiFile> columnFinder =
      elementMatcher == null || myInfo.lineNumber <= 0 ? null : new ExceptionColumnFinder(elementMatcher, myInfo.lineNumber - 1);
    HyperlinkInfo linkInfo =
      HyperlinkInfoFactory.getInstance().createMultipleFilesHyperlinkInfo(virtualFiles, myInfo.lineNumber - 1, myProject, columnFinder);
    Filter.Result result = new Filter.Result(highlightStartOffset, highlightEndOffset, linkInfo, myClassResolveInfo.myInLibrary);
    if (myMethod.startsWith("access$")) {
      myLocationRefiner = elementMatcher;
    }
    else if (myMethod.startsWith("lambda$")) {
      myLocationRefiner = new FunctionCallMatcher();
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

  private static int findFirstRParenAfterDigit(@NotNull String line) {
    int rParenIdx = -1;
    int rParenCandidate = line.lastIndexOf(')');
    //Looking for minimal position for ')' after a digit
    while (rParenCandidate > 0) {
      if (Character.isDigit(line.charAt(rParenCandidate - 1))) {
        rParenIdx = rParenCandidate;
      }
      rParenCandidate = line.lastIndexOf(')', rParenCandidate - 1);
    }
    return rParenIdx;
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
    int rParenIdx = findFirstRParenAfterDigit(line);
    if (rParenIdx < 0) return null;

    TextRange methodName = findMethodNameCandidateBefore(line, startIdx, rParenIdx);
    if (methodName == null) return null;

    int lParenIdx = methodName.getEndOffset();
    int dotIdx = methodName.getStartOffset() - 1;
    int moduleIdx = line.indexOf('/');
    int classNameIdx = moduleIdx > -1 && moduleIdx < dotIdx ? moduleIdx + 1 : startIdx + 1 + (startIdx >= 0 ? AT.length() : 0);

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

      int colonIndex = fileAndLine.lastIndexOf(':');
      if (colonIndex < 0) return null;

      int lineNumber = getLineNumber(fileAndLine.substring(colonIndex + 1));
      if (lineNumber < 0) return null;

      return new ParsedLine(classFqnRange, methodNameRange, fileLineRange, fileAndLine.substring(0, colonIndex).trim(), lineNumber);
    } 
  }
  
  private static class StackFrameMatcher implements ExceptionLineRefiner {
    private final String myMethodName;
    private final String myClassName;
    private final boolean myHasDollarInName;

    private StackFrameMatcher(@NotNull String line, @NotNull ParsedLine info) {
      myMethodName = info.methodNameRange.substring(line);
      myClassName = info.classFqnRange.substring(line);
      myHasDollarInName = StringUtil.getShortName(myClassName).contains("$");
    }

    @Override
    public boolean test(@NotNull PsiElement element) {
      if (!(element instanceof PsiIdentifier)) return false;
      if (myMethodName.equals("<init>")) {
        if (myHasDollarInName || element.textMatches(StringUtil.getShortName(myClassName))) {
          PsiElement parent = element.getParent();
          while (parent instanceof PsiJavaCodeReferenceElement) {
            parent = parent.getParent();
          }
          if (parent instanceof PsiAnonymousClass) {
            return isTargetClass(parent) || isTargetClass(((PsiAnonymousClass)parent).getSuperClass());
          }
          if (parent instanceof PsiNewExpression) {
            PsiJavaCodeReferenceElement ref = ((PsiNewExpression)parent).getClassOrAnonymousClassReference();
            return ref != null && isTargetClass(ref.resolve());
          }
        }
      }
      else if (element.textMatches(myMethodName)) {
        PsiElement parent = element.getParent();
        if (parent instanceof PsiReferenceExpression) {
          PsiElement target = ((PsiReferenceExpression)parent).resolve();
          return target instanceof PsiMethod && isTargetClass(((PsiMethod)target).getContainingClass());
        }
      }
      return false;
    }

    private boolean isTargetClass(PsiElement maybeClass) {
      if (!(maybeClass instanceof PsiClass)) return false;
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

  private static class ExceptionColumnFinder implements ToIntFunction<PsiFile> {
    private final Predicate<PsiElement> myElementMatcher;
    private final int myLineNumber;

    private ExceptionColumnFinder(@NotNull Predicate<PsiElement> elementMatcher, int lineNumber) {
      myElementMatcher = elementMatcher;
      myLineNumber = lineNumber;
    }

    @Override
    public int applyAsInt(PsiFile file) {
      if (DumbService.isDumb(file.getProject())) return 0; // may need to resolve refs
      Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
      if (document == null || document.getLineCount() <= myLineNumber) return 0;
      if (!PsiDocumentManager.getInstance(file.getProject()).isCommitted(document)) return 0;
      int startOffset = document.getLineStartOffset(myLineNumber);
      int endOffset = document.getLineEndOffset(myLineNumber);
      PsiElement element = file.findElementAt(startOffset);
      List<PsiElement> candidates = new ArrayList<>();
      while (element != null && element.getTextRange().getStartOffset() < endOffset) {
        if (myElementMatcher.test(element)) {
          candidates.add(element);
          if (candidates.size() > 1) return 0;
        }
        element = PsiTreeUtil.nextLeaf(element);
      }
      if (candidates.size() == 1) {
        return candidates.get(0).getTextRange().getStartOffset() - startOffset;
      }
      return 0;
    }
  }

  private static class FunctionCallMatcher implements ExceptionLineRefiner {
    @Override
    public boolean test(@NotNull PsiElement element) {
      if (!(element instanceof PsiIdentifier)) return false;
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiReferenceExpression)) return false;
      PsiMethodCallExpression call = ObjectUtils.tryCast(parent.getParent(), PsiMethodCallExpression.class);
      if (call == null) return false;
      PsiMethod target = call.resolveMethod();
      if (target == null) return false;
      return LambdaUtil.getFunctionalInterfaceMethod(target.getContainingClass()) == target;
    }
  }
}
