 package com.intellij.codeInsight.template;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.text.BlockSupport;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

 /**
  * @author mike
  */
 public class ExpressionUtil {
   private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.ExpressionUtil");

   private ExpressionUtil() {
   }

   @Nullable
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

   @Nullable
   private static String[] getNamesForIdentifier(Project project, PsiIdentifier identifier){
     if (identifier.getParent() instanceof PsiVariable){
       PsiVariable var = (PsiVariable)identifier.getParent();
       if (identifier.equals(var.getNameIdentifier())){
         CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
         VariableKind variableKind = codeStyleManager.getVariableKind(var);
         SuggestedNameInfo suggestedInfo = codeStyleManager.suggestVariableName(variableKind, null, var.getInitializer(), var.getType());
         return suggestedInfo.names;
       }
     }
     return null;
   }
 }
