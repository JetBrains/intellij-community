/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 30.05.2002
 * Time: 19:24:56
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util;

import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.IncorrectOperationException;

public class JavaDocPolicy {
  public static final int ASIS = 0;
  public static final int MOVE = 1;
  public static final int COPY = 2;

  private final int myJavaDocPolicy;

  public JavaDocPolicy(int javaDocPolicy) {
    myJavaDocPolicy = javaDocPolicy;
  }

  public void processCopiedJavaDoc(PsiDocComment newDocComment, PsiDocComment docComment, boolean willOldBeDeletedAnyway)
          throws IncorrectOperationException{
    if(myJavaDocPolicy == COPY || docComment == null) return;

    if(myJavaDocPolicy == MOVE) {
      docComment.delete();
    }
    else if(myJavaDocPolicy == ASIS && newDocComment != null && !willOldBeDeletedAnyway) {
      newDocComment.delete();
    }
  }

  public void processNewJavaDoc(PsiDocComment newDocComment) throws IncorrectOperationException {
    if(myJavaDocPolicy == ASIS && newDocComment != null) {
      newDocComment.delete();
    }
  }

  public void processOldJavaDoc(PsiDocComment oldDocComment) throws IncorrectOperationException {
    if(myJavaDocPolicy == MOVE && oldDocComment != null) {
      oldDocComment.delete();
    }
  }
}
