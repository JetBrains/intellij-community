// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.text.StringUtil.getShortName;

final public class FindDivergedExceptionLineHandler extends AnAction {

  private static final String LAMBDA_KEYWORD = "lambda$";
  private final PsiFile myPsiFile;
  private final MetaInfo myMetaInfo;
  private final ExceptionLineRefiner myRefiner;
  private final Editor myEditor;

  private FindDivergedExceptionLineHandler(@NotNull PsiFile file,
                                           @NotNull MetaInfo metaInfo,
                                           @NotNull ExceptionLineRefiner refiner,
                                           @NotNull Editor targetEditor) {

    super(null, JavaBundle.message("action.find.similar.stack.call.diverged", getShortName(metaInfo.className), metaInfo.methodName), null);
    this.myPsiFile = file;
    this.myMetaInfo = metaInfo;
    this.myRefiner = refiner;
    this.myEditor = targetEditor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Document document = PsiDocumentManager.getInstance(myPsiFile.getProject()).getDocument(myPsiFile);
    if (document == null) {
      return;
    }
    AtomicBoolean moreThanOne = new AtomicBoolean(false);
    boolean found = new PsiTargetNavigator<>(collector())
      .presentationProvider(element -> {
        String text;
        @NonNls String containerText;
        int lineNumber = document.getLineNumber(element.getTextRange().getEndOffset());
        if (element instanceof PsiMethod psiMethod) {
          text = psiMethod.getName();
          containerText = getShortName(myMetaInfo.className) + ": " + (lineNumber + 1);
        }
        else {
          PsiExpression expression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
          PsiElement textElement = expression != null ? expression : element;
          TextRange textRange = textElement.getTextRange()
            .intersection(new TextRange(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber)));
          if (textRange == null) {
            textRange = element.getTextRange();
          }
          text = document.getText(textRange).strip();
          containerText = getShortName(myMetaInfo.className) + "." + myMetaInfo.methodName + "()" +
                          ": " + (lineNumber + 1);
        }
        return TargetPresentation
          .builder(text)
          .containerText(containerText)
          .presentation();
      })
      .elementsConsumer((elements, navigator) -> {
        if (elements.isEmpty()) {
          return;
        }
        if (elements.size() > 1) {
          moreThanOne.set(true);
        }
        PsiElement next = elements.iterator().next();
        String message;
        if (next instanceof PsiMethod) {
          message = JavaBundle.message("action.find.similar.stack.call.methods", getShortName(myMetaInfo.className), myMetaInfo.methodName);
        }
        else {
          message =
            JavaBundle.message("action.find.similar.stack.call.similar.calls", getShortName(myMetaInfo.className), myMetaInfo.methodName);
        }
        navigator.title(message);
        navigator.tabTitle(message);
      })
      .navigate(myEditor, null, element -> {
        if (!moreThanOne.get() && element instanceof PsiMethod) {
          //there is no reason to jump if the caret is already inside
          int offset = myEditor.getCaretModel().getOffset();
          PsiElement caretElement = myPsiFile.findElementAt(offset);
          if (caretElement != null && PsiTreeUtil.isAncestor(element, caretElement, false)) {
            showNotFound(JavaBundle.message("action.find.similar.stack.call.location.not.found", getShortName(myMetaInfo.className),
                                            myMetaInfo.methodName));
            return true;
          }
        }
        return EditSourceUtil.navigateToPsiElement(element);
      });
    if (!found) {
      showNotFound(
        JavaBundle.message("action.find.similar.stack.call.methods.not.found", getShortName(myMetaInfo.className), myMetaInfo.methodName));
    }
  }

  private void showNotFound(@NlsContexts.HintText String message) {
    RelativePoint popupLocation = JBPopupFactory.getInstance().guessBestPopupLocation(myEditor);
    final JComponent label = HintUtil.createWarningLabel(message.replace("<", "&lt;").replace(">", "&gt;"));
    label.setBorder(JBUI.Borders.empty(2, 7));
    JBPopupFactory.getInstance().createBalloonBuilder(label)
      .setFadeoutTime(4000)
      .setFillColor(HintUtil.getWarningColor())
      .createBalloon()
      .show(popupLocation, Balloon.Position.above);
  }

  @VisibleForTesting
  @NotNull
  public Supplier<Collection<PsiElement>> collector() {
    return () -> {
      Set<PsiElement> result = new HashSet<>();
      List<PsiElement> startPoints = new ArrayList<>();
      JavaRecursiveElementVisitor fileVisitor = new JavaRecursiveElementVisitor() {

        @Override
        public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
          if (myMetaInfo.callType == MetaInfoCallType.STATIC_INIT) {
            startPoints.add(initializer);
          }
        }

        @Override
        public void visitField(@NotNull PsiField field) {
          if (myMetaInfo.callType == MetaInfoCallType.STATIC_INIT && field.hasModifierProperty(PsiModifier.STATIC)) {
            startPoints.add(field);
          }
          if (myMetaInfo.callType == MetaInfoCallType.NON_STATIC_INIT && !field.hasModifier(JvmModifier.STATIC)) {
            startPoints.add(field);
          }
        }

        @Override
        public void visitMethod(@NotNull PsiMethod method) {
          ProgressManager.checkCanceled();
          if (myMetaInfo.callType != MetaInfoCallType.LAMBDA && placeMatch(method, myMetaInfo)) {
            startPoints.add(method);
          }

          if (myMetaInfo.callType == MetaInfoCallType.LAMBDA) {
            super.visitMethod(method);
          }
        }

        @Override
        public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
          ProgressManager.checkCanceled();
          if (myMetaInfo.callType == MetaInfoCallType.LAMBDA) {
            PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
            if (method != null && placeMatch(method, myMetaInfo)) {
              startPoints.add(expression);
            }
          }
        }
      };
      myPsiFile.acceptChildren(fileVisitor);
      for (PsiElement startPoint : startPoints) {
        PsiTreeUtil.processElements(startPoint, new PsiElementProcessor<>() {
          @Override
          public boolean execute(@NotNull PsiElement element) {
            ProgressManager.checkCanceled();
            ExceptionLineRefiner.RefinerMatchResult matchedElement = myRefiner.matchElement(element);
            if (matchedElement != null && matchedElement.target() instanceof Navigatable) {
              PsiElement target = matchedElement.target();
              PsiMethod matchedElementMethod = PsiTreeUtil.getParentOfType(target, PsiMethod.class);
              if (matchedElementMethod != null && placeMatch(matchedElementMethod, myMetaInfo)) {
                result.add(target);
              }
              if (matchedElementMethod == null &&
                  (myMetaInfo.callType == MetaInfoCallType.NON_STATIC_INIT || myMetaInfo.callType == MetaInfoCallType.STATIC_INIT)) {
                result.add(target);
              }
            }
            return true;
          }
        });
      }
      if (result.isEmpty()) {
        result.addAll(startPoints);
      }
      return result.stream().sorted(Comparator.comparing(element -> element.getTextOffset())).collect(Collectors.toList());
    };
  }

  private static boolean placeMatch(@NotNull PsiElement element,
                                    @NotNull MetaInfo metaInfo) {
    PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass == null) return false;
    if (!metaInfo.className.equals(psiClass.getQualifiedName())) return false;
    if (element instanceof PsiMethod psiMethod) {
      return (metaInfo.methodName.equals(psiMethod.getName()) ||
              (metaInfo.callType == MetaInfoCallType.NON_STATIC_INIT && psiMethod.isConstructor()));
    }
    return metaInfo.callType == MetaInfoCallType.NON_STATIC_INIT || metaInfo.callType == MetaInfoCallType.STATIC_INIT;
  }

  static ExceptionLineParserImpl.@Nullable LinkInfo createLinkInfo(@Nullable PsiFile file,
                                                                   @Nullable String className,
                                                                   @Nullable String methodName,
                                                                   @Nullable ExceptionLineRefiner refiner,
                                                                   int lineStart, int lineEnd,
                                                                   @Nullable Editor targetEditor) {
    if (file == null || !file.isValid()) return null;
    FindDivergedExceptionLineHandler methodHandler =
      getFindMethodHandler(file, className, methodName, refiner, lineStart, lineEnd, targetEditor);
    if (methodHandler == null) return null;
    return new ExceptionLineParserImpl.LinkInfo(file, null, null, methodHandler, finder -> {
    });
  }

  @Nullable
  public static FindDivergedExceptionLineHandler getFindMethodHandler(@Nullable PsiFile file,
                                                                      @Nullable String className,
                                                                      @Nullable String methodName,
                                                                      @Nullable ExceptionLineRefiner refiner,
                                                                      int lineStart,
                                                                      int lineEnd,
                                                                      @Nullable Editor targetEditor) {

    //it can be ambiguous to find lambda anonymous classes
    if (file == null || className == null || methodName == null || refiner == null || targetEditor == null) {
      return null;
    }

    if (DumbService.isDumb(file.getProject())) {
      return null;
    }

    if (!file.getLanguage().is(JavaLanguage.INSTANCE)) {
      return null;
    }

    if (canBeBridge(file, lineStart, lineEnd, methodName)) {
      return null;
    }

    if (hasAnonymousClass(className)) return null;

    className = className.replaceAll("\\$", ".");

    MetaInfoCallType callType = MetaInfoCallType.ORDINARY;
    if (methodName.contains("$")) {
      int lambdaIndex = methodName.indexOf(LAMBDA_KEYWORD);
      if (lambdaIndex != -1) {
        callType = MetaInfoCallType.LAMBDA;
        methodName = methodName.substring(lambdaIndex + LAMBDA_KEYWORD.length());
        int nextDelimiter = methodName.indexOf("$");
        if (nextDelimiter != -1) {
          methodName = methodName.substring(0, nextDelimiter);
          if (methodName.contains("$") || methodName.equals("static") || methodName.equals("new")) return null;
        }
      }
      else {
        return null;
      }
      if (methodName.isEmpty()) {
        return null;
      }
    }

    callType = findInitCallType(methodName, callType);

    MetaInfo metaInfo = new MetaInfo(className, methodName, callType);

    if (skipByRefiner(file, refiner, lineStart, lineEnd)) return null;

    return new FindDivergedExceptionLineHandler(file, metaInfo, refiner, targetEditor);
  }

  @NotNull
  private static MetaInfoCallType findInitCallType(@NotNull String methodName, @NotNull MetaInfoCallType callType) {
    if (callType == MetaInfoCallType.ORDINARY) {
      if ("<init>".equals(methodName)) {
        callType = MetaInfoCallType.NON_STATIC_INIT;
      }
      else if ("<clinit>".equals(methodName)) {
        callType = MetaInfoCallType.STATIC_INIT;
      }
    }
    return callType;
  }

  private static boolean skipByRefiner(@NotNull PsiFile file,
                                       @NotNull ExceptionLineRefiner refiner,
                                       int lineStart,
                                       int lineEnd) {
    if (refiner.getExceptionInfo() != null) {
      PsiElement element = file.findElementAt(lineStart);
      Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      if (document == null) {
        return true;
      }
      while (element != null && element.getTextRange().getStartOffset() < lineEnd) {
        PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiStatement.class, PsiField.class);
        if (parent != null) {
          int lineNumberStart = document.getLineNumber(parent.getTextOffset());
          int lineNumberEnd = document.getLineNumber(parent.getTextOffset() + parent.getTextLength());
          if (lineNumberStart != lineNumberEnd) {
            return true;
          }
        }
        element = PsiTreeUtil.nextLeaf(element);
      }
    }
    return false;
  }

  private static boolean canBeBridge(PsiFile file, int lineStart, int lineEnd, String methodName) {
    if (!file.isValid()) return false;
    PsiElement element = file.findElementAt(lineStart);
    while (element != null && element.getTextRange().getStartOffset() < lineEnd) {
      PsiElement finalElement = element;
      if (finalElement instanceof PsiKeyword keyword && keyword.getTokenType().equals(JavaTokenType.CLASS_KEYWORD)) {
        PsiElement parent = finalElement.getParent();
        if (parent instanceof PsiClass targetClass &&
            (targetClass.getExtendsListTypes().length > 0 || targetClass.getImplementsListTypes().length > 0) &&
            ContainerUtil.exists(targetClass.getMethods(), psiMethod -> methodName.equals(psiMethod.getName()))) {
          return true;
        }
      }
      element = PsiTreeUtil.nextLeaf(element);
    }
    return false;
  }

  private static boolean hasAnonymousClass(@NotNull String className) {
    int classAdditionalInfoIndex = className.indexOf("$");
    if (classAdditionalInfoIndex != -1) {
      String shortName = getShortName(className, '$');
      if (shortName.isEmpty()) {
        return true;
      }
      if (Character.isDigit(shortName.charAt(0))) {
        return true;
      }
    }
    return false;
  }

  private record MetaInfo(@NonNls String className, @NonNls String methodName, @NotNull MetaInfoCallType callType) {
  }

  private enum MetaInfoCallType {
    LAMBDA, STATIC_INIT, NON_STATIC_INIT, ORDINARY
  }
}
