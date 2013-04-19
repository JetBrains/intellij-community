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
package com.intellij.psi.search;

import com.intellij.psi.PsiReference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public interface PsiReferenceProcessor{
  boolean execute(PsiReference element);

  class CollectElements implements PsiReferenceProcessor{
    private final Collection<PsiReference> myCollection;

    public CollectElements(Collection<PsiReference> collection) {
      myCollection = Collections.synchronizedCollection(collection);
    }

    public CollectElements() {
      this(new ArrayList<PsiReference>());
    }

    public PsiReference[] toArray(){
      return myCollection.toArray(new PsiReference[myCollection.size()]);
    }

    public PsiReference[] toArray(PsiReference[] array){
      return myCollection.toArray(array);
    }

    @Override
    public boolean execute(PsiReference element) {
      myCollection.add(element);
      return true;
    }
  }

  class FindElement implements PsiReferenceProcessor{
    private volatile PsiReference myFoundElement = null;

    public boolean isFound() {
      return myFoundElement != null;
    }

    public PsiReference getFoundReference() {
      return myFoundElement;
    }

    @Override
    public boolean execute(PsiReference element) {
      myFoundElement = element;
      return false;
    }
  }
}