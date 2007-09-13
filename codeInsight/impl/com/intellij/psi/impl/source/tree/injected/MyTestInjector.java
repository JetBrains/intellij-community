/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Sep 4, 2007
 * Time: 7:17:07 PM
 */
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.injection.ConcatenationAwareInjector;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.PsiCommentImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class MyTestInjector {
  private LanguageInjector myInjector;
  private ConcatenationAwareInjector myQLInPlaceInjector;
  private ConcatenationAwareInjector myJSInPlaceInjector;
  private ConcatenationAwareInjector mySeparatedJSInjector;
  private final PsiManager myPsiManager;

  public MyTestInjector(PsiManager psiManager) {
    myPsiManager = psiManager;
  }

  public void injectAll() {
    myInjector = injectVariousStuffEverywhere(myPsiManager);

    Project project = myPsiManager.getProject();
    Language ql = findLanguageByID("FQL");
    Language js = findLanguageByID("JavaScript");
    myQLInPlaceInjector = registerForStringVarInitializer(project, ql, "ql", null, null);
    myJSInPlaceInjector = registerForStringVarInitializer(project, js, "js", null, null);
    mySeparatedJSInjector = registerForStringVarInitializer(project, js, "jsSeparated", " + ", " + 'separator'");
  }

  private static ConcatenationAwareInjector registerForStringVarInitializer(Project project, final Language language,
                                                                                               @NonNls final String varName,
                                                                                               final String prefix, @NonNls final String suffix) {
    ConcatenationAwareInjector injector = new ConcatenationAwareInjector() {
      public void getLanguagesToInject(@NotNull MultiHostRegistrar injectionPlacesRegistrar,
                                       @NotNull PsiElement... operands) {
        PsiVariable variable = PsiTreeUtil.getParentOfType(operands[0], PsiVariable.class);
        if (variable == null) return;
        if (!varName.equals(variable.getName())) return;
        if (!(operands[0] instanceof PsiLiteralExpression)) return;
        injectionPlacesRegistrar.startInjecting(language);
        for (int i = 0; i < operands.length; i++) {
          PsiElement operand = operands[i];
          if (!(operand instanceof PsiLanguageInjectionHost)) continue;
          TextRange textRange = new TextRange(1, operand.getTextLength() - 1);
          injectionPlacesRegistrar.addPlace(i == 0 ? null : prefix, i == operands.length - 1 ? null : suffix, (PsiLanguageInjectionHost)operand, textRange);
        }
        injectionPlacesRegistrar.doneInjecting();
      }
    };
    InjectedLanguageManager.getInstance(project).registerConcatenationInjector(injector);
    return injector;
  }


  public void uninjectAll() {
    myPsiManager.unregisterLanguageInjector(myInjector);
    Project project = myPsiManager.getProject();
    boolean b = InjectedLanguageManager.getInstance(project).unregisterConcatenationInjector(myQLInPlaceInjector);
    assert b;
    b = InjectedLanguageManager.getInstance(project).unregisterConcatenationInjector(myJSInPlaceInjector);
    assert b;
    b = InjectedLanguageManager.getInstance(project).unregisterConcatenationInjector(mySeparatedJSInjector);
    assert b;
  }

  private static Language findLanguageByID(@NonNls String name) {
    for (Language language : Language.getRegisteredLanguages()) {
      if (language.getID().equals(name)) return language;
    }
    return null;
  }
  private static LanguageInjector injectVariousStuffEverywhere(PsiManager psiManager) {
    LanguageInjector myInjector = new LanguageInjector() {
      public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces placesToInject) {
        Language ql = findLanguageByID("FQL");
        Language js = findLanguageByID("JavaScript");
        if (host instanceof XmlAttributeValue) {
          XmlAttributeValue value = (XmlAttributeValue)host;
          PsiElement parent = value.getParent();
          if (parent instanceof XmlAttribute) {
            @NonNls String attrName = ((XmlAttribute)parent).getLocalName();
            if ("ql".equals(attrName)) {
              inject(host, placesToInject, ql);
              return;
            }
            if ("js".equals(attrName)) {
              inject(host, placesToInject, js);
              return;
            }
            if ("jsInBraces".equals(attrName)) {
              String text = value.getText();
              int index=0;
              while (text.indexOf('{', index) != -1) {
                int lbrace = text.indexOf('{', index);
                int rbrace = text.indexOf('}', index);
                placesToInject.addPlace(js, new TextRange(lbrace + 1, rbrace), null, null);
                index = rbrace + 1;
              }
              return;
            }
          }
        }
        if (host instanceof XmlText) {
          // inject to xml tags named 'ql'
          final XmlText xmlText = (XmlText)host;
          XmlTag tag = xmlText.getParentTag();
          if (tag == null) return;
          if ("ql".equals(tag.getLocalName())) {
            TextRange textRange = new TextRange(0, xmlText.getTextLength());
            placesToInject.addPlace(ql, textRange, null, null);
            return;
          }
          if ("js".equals(tag.getLocalName())) {
            TextRange textRange = new TextRange(0, xmlText.getTextLength());
            placesToInject.addPlace(js, textRange, null, null);
            return;
          }
          if ("jsprefix".equals(tag.getLocalName())) {
            TextRange textRange = new TextRange(0, xmlText.getTextLength());
            placesToInject.addPlace(js, textRange, "function foo(doc, window){", "}");
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
        if (host instanceof PsiCommentImpl) {
          String text = host.getText();
          if (text.startsWith("/*-{") && text.endsWith("}-*/")) {
            TextRange textRange = new TextRange(4, text.length()-4);
            if (!(host.getParent()instanceof PsiMethod)) return;
            PsiMethod method = (PsiMethod)host.getParent();
            if (!method.hasModifierProperty(PsiModifier.NATIVE) || !method.hasModifierProperty(PsiModifier.PUBLIC)) return;
            String paramList = "";
            for (PsiParameter parameter : method.getParameterList().getParameters()) {
              if (paramList.length()!=0) paramList += ",";
              paramList += parameter.getName();
            }
            @NonNls String header = "function " + method.getName() + "("+paramList+") {";
            placesToInject.addPlace(js, textRange, header, "}");
            return;
          }
        }
        // inject to all string literal initializers of variables named 'ql'
        if (host instanceof PsiLiteralExpression) {
          PsiVariable variable = PsiTreeUtil.getParentOfType(host, PsiVariable.class);
          if (variable == null) return;
          if (host.getParent() instanceof PsiBinaryExpression) return;
          if ("ql".equals(variable.getName())) {
            TextRange textRange = new TextRange(1, host.getTextLength() - 1);
            placesToInject.addPlace(ql, textRange, null, null);
          }
          if ("xml".equals(variable.getName())) {
            TextRange textRange = new TextRange(1, host.getTextLength() - 1);
            placesToInject.addPlace(StdLanguages.XML, textRange, null, null);
          }
          if ("js".equals(variable.getName())) { // with prefix/suffix
            TextRange textRange = new TextRange(1, host.getTextLength() - 1);
            placesToInject.addPlace(js, textRange, "function foo(doc,window) {", "}");
          }

          if ("lang".equals(variable.getName())) {
            // various lang depending on field "languageID" content
            PsiClass aClass = PsiTreeUtil.getParentOfType(variable, PsiClass.class);
            aClass = aClass.findInnerClassByName("Language", false);
            String text = aClass.getInitializers()[0].getBody().getFirstBodyElement().getNextSibling().getText().substring(2);
            Language language = findLanguageByID(text);

            TextRange textRange = new TextRange(1, host.getTextLength() - 1);
            placesToInject.addPlace(language, textRange, "", "");
          }
        }
      }
    };

    psiManager.registerLanguageInjector(myInjector);

    return myInjector;
  }

  private static void inject(final PsiLanguageInjectionHost host, final InjectedLanguagePlaces placesToInject, final Language language) {
    ASTNode[] children = ((ASTNode)host).getChildren(null);
    TextRange insideQuotes = new TextRange(0, host.getTextLength());

    if (children.length > 1 && children[0].getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {
      insideQuotes = new TextRange(children[1].getTextRange().getStartOffset() - host.getTextRange().getStartOffset(), insideQuotes.getEndOffset());
    }
    if (children.length > 1 && children[children.length-1].getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
      insideQuotes = new TextRange(insideQuotes.getStartOffset(), children[children.length-2].getTextRange().getEndOffset() - host.getTextRange().getStartOffset());
    }

    placesToInject.addPlace(language, insideQuotes, null, null);
  }

}