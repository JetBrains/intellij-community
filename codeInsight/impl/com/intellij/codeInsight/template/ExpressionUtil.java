 package com.intellij.codeInsight.template;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.text.BlockSupport;
import com.intellij.util.IncorrectOperationException;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

 /**
  * @author mike
  */
 public class ExpressionUtil {
   private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.ExpressionUtil");

   public static String[] getNames(final ExpressionContext context) {
     Project project = context.getProject();
     int offset = context.getStartOffset();

     PsiDocumentManager.getInstance(project).commitAllDocuments();

     String[] names = null;
     PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
     PsiElement element = file.findElementAt(offset);
     if (element instanceof PsiIdentifier){
       names = getNamesForIdentifier(project, (PsiIdentifier)element);
     }
     else{
       PsiFile fileCopy = (PsiFile)file.copy();
       BlockSupport blockSupport = project.getComponent(BlockSupport.class);
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

   private static String[] getNamesForIdentifier(Project project, PsiIdentifier identifier){
     if (identifier.getParent() instanceof PsiVariable){
       PsiVariable var = (PsiVariable)identifier.getParent();
       if (var.getNameIdentifier().equals(identifier)) {
         CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
         VariableKind variableKind = codeStyleManager.getVariableKind(var);
         SuggestedNameInfo suggestedInfo = codeStyleManager.suggestVariableName(variableKind, null, var.getInitializer(), var.getType());
         if (var instanceof PsiParameter && ((PsiParameter)var).getDeclarationScope() instanceof PsiForeachStatement) {
           final PsiExpression iteratedValue = ((PsiForeachStatement)((PsiParameter)var).getDeclarationScope()).getIteratedValue();
           if (iteratedValue instanceof PsiReferenceExpression) {
             final String referenceName = ((PsiReferenceExpression)iteratedValue).getReferenceName();
             if (referenceName != null) {
               final Set<String> names = new HashSet<String>(Arrays.asList(suggestedInfo.names));
               names.add(StringUtil.unpluralize(referenceName));
               return names.toArray(new String[names.size()]);
             }
           }
         }
         return suggestedInfo.names; //TODO: callback about choosen name
       }
     }
     return null;
   }
 }
