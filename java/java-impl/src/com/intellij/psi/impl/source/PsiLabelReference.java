/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl.source;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 06.05.2003
 * Time: 23:32:45
 * To change this template use Options | File Templates.
 */
public class PsiLabelReference implements PsiReference{
  private final PsiStatement myStatement;
  private PsiIdentifier myIdentifier;

  public PsiLabelReference(PsiStatement stat, PsiIdentifier identifier){
    myStatement = stat;
    myIdentifier = identifier;
  }

  public PsiElement getElement(){
    return myStatement;
  }

  public TextRange getRangeInElement(){
    final int parent = myIdentifier.getStartOffsetInParent();
    return new TextRange(parent, myIdentifier.getTextLength() + parent);
  }

    public PsiElement resolve(){
      final String label = myIdentifier.getText();
      if(label == null) return null;
      PsiElement context = myStatement;
      while(context != null){
        if(context instanceof PsiLabeledStatement){
          final PsiLabeledStatement statement = (PsiLabeledStatement) context;
          if(label.equals(statement.getName()))
            return statement;
        }
        context = context.getContext();
      }
      return null;
    }

    public String getCanonicalText(){
      return getElement().getText();
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException{
      myIdentifier = (PsiIdentifier) PsiImplUtil.setName(myIdentifier, newElementName);
      return myIdentifier;
    }

    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException{
      if(element instanceof PsiLabeledStatement){
        myIdentifier = (PsiIdentifier) PsiImplUtil.setName(myIdentifier, ((PsiLabeledStatement)element).getName());
        return myIdentifier;
      }
      throw new IncorrectOperationException("Can't bind not to labeled statement");
    }

    public boolean isReferenceTo(PsiElement element){
      return resolve() == element;
    }

    @NotNull
    public Object[] getVariants(){
      final List result = new ArrayList();
      PsiElement context = myStatement;
      while(context != null){
        if(context instanceof PsiLabeledStatement){
          result.add(context);
        }
        context = context.getContext();
      }
      return result.toArray();
    }

  public boolean isSoft(){
    return false;
  }
}
