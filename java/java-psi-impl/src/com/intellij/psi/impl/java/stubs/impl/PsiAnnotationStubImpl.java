/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.reference.SoftReference;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;

/**
 * @author max
 */
public class PsiAnnotationStubImpl extends StubBase<PsiAnnotation> implements PsiAnnotationStub {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.java.stubs.impl.PsiAnnotationStubImpl");

  static {
    CharTableImpl.addStringsFromClassToStatics(AnnotationUtil.class);
    CharTableImpl.staticIntern("@NotNull");
    CharTableImpl.staticIntern("@Nullable");
    CharTableImpl.staticIntern("@Override");
  }

  private final String myText;
  private SoftReference<PsiAnnotation> myParsedFromRepository;

  public PsiAnnotationStubImpl(StubElement parent, String text) {
    super(parent, JavaStubElementTypes.ANNOTATION);
    CharSequence interned = CharTableImpl.getStaticInterned(text);
    myText = interned == null ? text : interned.toString();
  }

  @Override
  public String getText() {
    return myText;
  }

  @Override
  public PsiAnnotation getPsiElement() {
    PsiAnnotation annotation = SoftReference.dereference(myParsedFromRepository);
    if (annotation != null) return annotation;

    String text = getText();
    try {
      PsiJavaParserFacade facade = JavaPsiFacade.getInstance(getProject()).getParserFacade();
      annotation = facade instanceof PsiElementFactoryImpl
          ? ((PsiElementFactoryImpl) facade).createAnnotationFromText(text, getPsi(), false)
          : facade.createAnnotationFromText(text, getPsi());
      ((LightVirtualFile)annotation.getContainingFile().getViewProvider().getVirtualFile()).setWritable(false);
      myParsedFromRepository = new SoftReference<>(annotation);
      return annotation;
    }
    catch (IncorrectOperationException e) {
      LOG.error("Bad annotation in " + fileName(), e);
      return null;
    }
  }

  private String fileName() {
    StubElement<?> stub = this;
    while ((stub = stub.getParentStub()) != null) {
      if (stub instanceof PsiFileStub) {
        Object psi = stub.getPsi();
        if (psi instanceof PsiFile) {
          VirtualFile file = ((PsiFile)psi).getVirtualFile();
          return file != null ? file.getUrl() : ((PsiFile)psi).getName();
        }
      }
    }

    return "<unknown file>";
  }

  @Override
  public String toString() {
    return "PsiAnnotationStub[" + myText + ']';
  }
}