/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 30.05.2002
 * Time: 19:24:56
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util;

import com.intellij.psi.PsiComment;
import com.intellij.util.IncorrectOperationException;

public class DocCommentPolicy<T extends PsiComment> {
  public static final int ASIS = 0;
  public static final int MOVE = 1;
  public static final int COPY = 2;

  private final int myJavaDocPolicy;

  public DocCommentPolicy(int javaDocPolicy) {
    myJavaDocPolicy = javaDocPolicy;
  }

  public void processCopiedJavaDoc(T newDocComment, T docComment, boolean willOldBeDeletedAnyway)
          throws IncorrectOperationException{
    if(myJavaDocPolicy == COPY || docComment == null) return;

    if(myJavaDocPolicy == MOVE) {
      docComment.delete();
    }
    else if(myJavaDocPolicy == ASIS && newDocComment != null && !willOldBeDeletedAnyway) {
      newDocComment.delete();
    }
  }

  public void processNewJavaDoc(T newDocComment) throws IncorrectOperationException {
    if(myJavaDocPolicy == ASIS && newDocComment != null) {
      newDocComment.delete();
    }
  }

  public void processOldJavaDoc(T oldDocComment) throws IncorrectOperationException {
    if(myJavaDocPolicy == MOVE && oldDocComment != null) {
      oldDocComment.delete();
    }
  }
}
