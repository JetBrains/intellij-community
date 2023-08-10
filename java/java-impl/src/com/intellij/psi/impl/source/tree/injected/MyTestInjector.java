// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.injection.ConcatenationAwareInjector;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.PsiCommentImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Collections;
import java.util.List;

@TestOnly
public class MyTestInjector {
  private final PsiManager myPsiManager;

  @TestOnly
  public MyTestInjector(@NotNull PsiManager psiManager) {
    myPsiManager = psiManager;
  }

  public void injectAll(@NotNull Disposable parent) {
    injectVariousStuffEverywhere(parent, myPsiManager);

    Project project = myPsiManager.getProject();
    Language ql = Language.findLanguageByID("JPAQL");
    Language js = Language.findLanguageByID("JavaScript");
    registerForStringVarInitializer(parent, project, ql, "ql", null, null);
    registerForStringVarInitializer(parent, project, ql, "qlPrefixed", "xxx", null);
    registerForStringVarInitializer(parent, project, js, "js", null, null);
    registerForStringVarInitializer(parent, project, js, "jsSeparated", " + ", " + 'separator'");
    registerForStringVarInitializer(parent, project, js, "jsBrokenPrefix", "xx ", "");

    registerForStringVarInitializer(parent, project, Language.findLanguageByID("Oracle"), "oracle", null, null);

    registerForParameterValue(parent, project, Language.findLanguageByID("Groovy"), "groovy");
    registerForStringVarInitializer(parent, project, JavaLanguage.INSTANCE, "java", "", "");
  }

  private static void registerForParameterValue(Disposable parent, final Project project, final Language language, final String paramName) {
    if (language == null) return;
    final ConcatenationAwareInjector injector = (injectionPlacesRegistrar, operands) -> {
      PsiElement operand = operands[0];
      if (!(operand instanceof PsiLiteralExpression)) return;
      if (!(operand.getParent() instanceof PsiExpressionList expressionList)) return;
      int i = ArrayUtil.indexOf(expressionList.getExpressions(), operand);
      if (!(operand.getParent().getParent() instanceof PsiMethodCallExpression methodCallExpression)) return;
      PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) return;
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (i>=parameters.length) return;
      PsiParameter parameter = parameters[i];
      if (!paramName.equals(parameter.getName())) return;
      TextRange textRange = textRangeToInject((PsiLanguageInjectionHost)operand);
      injectionPlacesRegistrar.startInjecting(language)
      .addPlace(null, null, (PsiLanguageInjectionHost)operand, textRange)
      .doneInjecting();
    };
    ConcatenationInjectorManager.EP_NAME.getPoint(project).registerExtension(injector, parent);
  }

  private static void registerForStringVarInitializer(@NotNull Disposable parent,
                                                      @NotNull final Project project,
                                                      final Language language,
                                                      @NotNull @NonNls final String varName,
                                                      @NonNls final String prefix,
                                                      @NonNls final String suffix) {
    if (language == null) return;
    final ConcatenationAwareInjector injector = (injectionPlacesRegistrar, operands) -> {
      if (operands[0].getParent() instanceof PsiNameValuePair) return; // do not inject into @SupressWarnings
      PsiVariable variable = PsiTreeUtil.getParentOfType(operands[0], PsiVariable.class);
      if (variable == null) return;
      if (!varName.equals(variable.getName())) return;
      if (!(operands[0] instanceof PsiLiteralExpression)) return;
      boolean started = false;
      String prefixFromPrev="";
      for (int i = 0; i < operands.length; i++) {
        PsiElement operand = operands[i];
        if (!(operand instanceof PsiLiteralExpression)) {
          continue;
        }
        Object value = ((PsiLiteralExpression)operand).getValue();
        if (!(value instanceof String)) {
          prefixFromPrev += value;
          continue;
        }
        TextRange textRange = textRangeToInject((PsiLanguageInjectionHost)operand);
        if (!started) {
          injectionPlacesRegistrar.startInjecting(language);
          started = true;
        }
        injectionPlacesRegistrar.addPlace(prefixFromPrev + (i == 0 ? "" : prefix==null?"":prefix), i == operands.length - 1 ? null : suffix, (PsiLanguageInjectionHost)operand, textRange);
        prefixFromPrev = "";
      }
      if (started) {
        injectionPlacesRegistrar.doneInjecting();
      }
    };
    ConcatenationInjectorManager.EP_NAME.getPoint(project).registerExtension(injector, parent);
  }

  private static void injectVariousStuffEverywhere(@NotNull Disposable parent, final PsiManager psiManager) {
    final Language ql = Language.findLanguageByID("JPAQL");
    final Language js = Language.findLanguageByID("JavaScript 1.8");
    final Language html = Language.findLanguageByID("HTML");
    if (ql == null || js == null) return;
    final Language ecma4 = Language.findLanguageByID("ECMA Script Level 4");

    InjectedLanguageManager.getInstance(psiManager.getProject()).registerMultiHostInjector(new MultiHostInjector() {
      @Override
      public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        XmlAttributeValue value = (XmlAttributeValue)context;
        PsiElement parent1 = value.getParent();
        if (parent1 instanceof XmlAttribute) {
          @NonNls String attrName = ((XmlAttribute)parent1).getLocalName();
          if ("jsInBraces".equals(attrName)) {
            registrar.startInjecting(js);
            String text = value.getText();
            int index = 0;
            while (text.indexOf('{', index) != -1) {
              int lbrace = text.indexOf('{', index);
              int rbrace = text.indexOf('}', index);
              registrar.addPlace("", "", (PsiLanguageInjectionHost)value, new TextRange(lbrace + 1, rbrace));
              index = rbrace + 1;
            }
            registrar.doneInjecting();
          }
        }
      }

      @Override
      @NotNull
      public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return Collections.singletonList(XmlAttributeValue.class);
      }
    }, parent);

    final LanguageInjector myInjector = (host, placesToInject) -> {
      if (host instanceof XmlAttributeValue value) {
        PsiElement parent1 = value.getParent();
        if (parent1 instanceof XmlAttribute) {
          @NonNls String attrName = ((XmlAttribute)parent1).getLocalName();
          if ("ql".equals(attrName)) {
            inject(host, placesToInject, ql);
            return;
          }
          if ("js".equals(attrName)) {
            inject(host, placesToInject, js);
            return;
          }

          if ("jsprefix".equals(attrName)) {
            inject(host, placesToInject, js, "function foo(doc, window){", "}");
            return;
          }
        }
      }
      if (host instanceof XmlText xmlText) {
        // inject to xml tags named 'ql'
        XmlTag tag = xmlText.getParentTag();
        if (tag == null) return;
        if ("ql".equals(tag.getLocalName())) {
          inject(host, placesToInject, ql);
          return;
        }
        if ("js".equals(tag.getLocalName())) {
          inject(host, placesToInject, js);
          return;
        }
        if ("htmlInject".equals(tag.getLocalName())) {
          inject(host, placesToInject, html);
          return;
        }
        if (ecma4 != null && "ecma4".equals(tag.getLocalName())) {
          inject(host, placesToInject, ecma4);
          return;
        }
        if ("jsprefix".equals(tag.getLocalName())) {
          inject(host, placesToInject, js, "function foo(doc, window){", "}");
          return;
        }

        if ("jsInHash".equals(tag.getLocalName())) {
          String text = xmlText.getText();
          if (text.contains("#")) {
            int start = text.indexOf('#');
            int end = text.lastIndexOf('#');
            if (start != end && start != -1) {
              placesToInject.addPlace(js, new TextRange(start + 1, end), null, null);
              return;
            }
          }
        }
      }

      if (host instanceof PsiComment && ((PsiComment)host).getTokenType() == JavaTokenType.C_STYLE_COMMENT) {
        /* {{{
         *   js code
         *   js code
         * }}}
         */
        String text = host.getText();
        String prefix = "/*\n * {{{\n";
        String suffix = " }}}\n */";
        if (text.startsWith(prefix) && text.endsWith(suffix)) {
          String s = StringUtil.trimEnd(StringUtil.trimStart(text, prefix), suffix);
          int off = 0;
          while (!s.isEmpty()) {
            String t = s.trim();
            if (t.startsWith("*")) t = t.substring(1).trim();
            int i = s.length() - t.length();
            off += i;
            int endOfLine = t.indexOf('\n');
            if (endOfLine == -1) endOfLine = t.length();
            placesToInject.addPlace(js, TextRange.from(prefix.length() + off, endOfLine), "", "\n");
            off += endOfLine;
            s = s.substring(i+endOfLine);
          }
          return;
        }
      }

      if (host instanceof PsiCommentImpl) {
        String text = host.getText();
        if (text.startsWith("/*--{") && text.endsWith("}--*/")) {
          TextRange textRange = new TextRange(4, text.length()-4);
          if (!(host.getParent() instanceof PsiMethod method)) return;
          if (!method.hasModifierProperty(PsiModifier.NATIVE) || !method.hasModifierProperty(PsiModifier.PUBLIC)) return;
          StringBuilder paramList = new StringBuilder();
          for (PsiParameter parameter : method.getParameterList().getParameters()) {
            if (paramList.length() > 0) paramList.append(",");
            paramList.append(parameter.getName());
          }
          @NonNls String header = "function " + method.getName() + "("+paramList+") {";
          Language gwt = Language.findLanguageByID("GWT JavaScript");
          placesToInject.addPlace(gwt, textRange, header, "}");
          return;
        }
        PsiElement parent1 = host.getParent();
        if (parent1 instanceof PsiMethod && ((PsiMethod)parent1).getName().equals("xml")) {
          placesToInject.addPlace(XMLLanguage.INSTANCE, new TextRange(2, host.getTextLength() - 2), null, null);
          return;
        }
      }

      // inject to all string literal initializers of variables named 'ql'
      if (host instanceof PsiLiteralExpression && ((PsiLiteralExpression)host).getValue() instanceof String) {
        PsiVariable variable = PsiTreeUtil.getParentOfType(host, PsiVariable.class);
        if (variable == null) return;
        if (host.getParent() instanceof PsiPolyadicExpression) return;
        if ("ql".equals(variable.getName())) {
          placesToInject.addPlace(ql, textRangeToInject(host), null, null);
        }
        if ("xml".equals(variable.getName())) {
          placesToInject.addPlace(XMLLanguage.INSTANCE, textRangeToInject(host), null, null);
        }
        if ("js".equals(variable.getName())) { // with prefix/suffix
          placesToInject.addPlace(js, textRangeToInject(host), "function foo(doc,window) {", "}");
        }

        if ("lang".equals(variable.getName())) {
          // various lang depending on field "languageID" content
          PsiClass aClass = PsiTreeUtil.getParentOfType(variable, PsiClass.class);
          aClass = aClass.findInnerClassByName("Language", false);
          String text = aClass.getInitializers()[0].getBody().getFirstBodyElement().getNextSibling().getText().substring(2);
          Language language = Language.findLanguageByID(text);

          if (language != null) {
            placesToInject.addPlace(language, textRangeToInject(host), "", "");
          }
        }
      }
    };

    // cannot use maskAll here because of InjectedLanguageEditingTest (ok for all other tests)
    //((ExtensionPointImpl<LanguageInjector>)LanguageInjector.EXTENSION_POINT_NAME.getPoint(null)).maskAll(Collections.singletonList(myInjector), parent);
    LanguageInjector.EXTENSION_POINT_NAME.getPoint().registerExtension(myInjector, parent);
  }

  private static void inject(final PsiLanguageInjectionHost host, final InjectedLanguagePlaces placesToInject, final Language language) {
    inject(host, placesToInject, language, null, null);
  }
  private static void inject(final PsiLanguageInjectionHost host, final InjectedLanguagePlaces placesToInject, final Language language, @NonNls String prefix, String suffix) {
    TextRange insideQuotes = textRangeToInject(host);

    placesToInject.addPlace(language, insideQuotes, prefix, suffix);
  }

  public static TextRange textRangeToInject(PsiLanguageInjectionHost host) {
    ASTNode[] children = host.getNode().getChildren(null);
    TextRange insideQuotes = new ProperTextRange(0, host.getTextLength());

    if (children.length > 1 && children[0].getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {
      insideQuotes = new ProperTextRange(children[1].getTextRange().getStartOffset() - host.getTextRange().getStartOffset(), insideQuotes.getEndOffset());
    }
    if (children.length > 1 && children[children.length-1].getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
      insideQuotes = new ProperTextRange(insideQuotes.getStartOffset(), children[children.length-2].getTextRange().getEndOffset() - host.getTextRange().getStartOffset());
    }
    if (host instanceof PsiLiteralExpression) {
      insideQuotes = new ProperTextRange(1, host.getTextLength()-1);
    }
    return insideQuotes;
  }
}
