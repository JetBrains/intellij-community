// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.text.BlockSupport;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

 public final class ExpressionUtil {
   private static final Logger LOG = Logger.getInstance(ExpressionUtil.class);

   private ExpressionUtil() {
   }

   public static String @Nullable [] getNames(final ExpressionContext context) {
     final Project project = context.getProject();
     final int offset = context.getStartOffset();

     Document document = context.getEditor().getDocument();
     PsiDocumentManager.getInstance(project).commitDocument(document);

     String[] names = null;
     PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
     PsiElement element = file.findElementAt(offset);
     if (element instanceof PsiIdentifier){
       names = getNamesForIdentifier(project, (PsiIdentifier)element);
     }
     else{
       final PsiFile fileCopy = (PsiFile)file.copy();
       BlockSupport blockSupport = BlockSupport.getInstance(project);
       try{
         blockSupport.reparseRange(fileCopy, offset, offset, "xxx");
       }
       catch(IncorrectOperationException e){
         LOG.error(e);
       }
       PsiElement identifierCopy = fileCopy.findElementAt(offset);
       if (identifierCopy instanceof PsiIdentifier) {
         names = getNamesForIdentifier(project, (PsiIdentifier)identifierCopy);
       }
     }
     return names;
   }

   private static String @Nullable [] getNamesForIdentifier(Project project, PsiIdentifier identifier){
     if (identifier.getParent() instanceof PsiVariable var && identifier.equals(var.getNameIdentifier())) {
       JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
       VariableKind variableKind = codeStyleManager.getVariableKind(var);
       PsiExpression initializer = var.getInitializer();
       if (var instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiForeachStatement foreachStatement) {
         //synthesize initializer
         final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
         if (iteratedValue != null) {
           try {
             final PsiArrayAccessExpression expr =
               (PsiArrayAccessExpression)JavaPsiFacade.getElementFactory(iteratedValue.getProject()).createExpressionFromText("a[0]", var);
             expr.getArrayExpression().replace(iteratedValue);
             initializer = expr; //note: non physical with no parent
           }
           catch (IncorrectOperationException e) {
             //do nothing
           }
         }
       }
       SuggestedNameInfo suggestedInfo = codeStyleManager.suggestVariableName(variableKind, null, initializer, var.getType());
       return suggestedInfo.names;
     }
     return null;
   }
 }
