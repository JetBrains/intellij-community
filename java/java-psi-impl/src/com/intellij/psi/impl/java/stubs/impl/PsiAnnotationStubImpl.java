// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.impl;

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
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.SoftReference;

import static com.intellij.reference.SoftReference.dereference;

public class PsiAnnotationStubImpl extends StubBase<PsiAnnotation> implements PsiAnnotationStub {
  private static final Logger LOG = Logger.getInstance(PsiAnnotationStubImpl.class);

  private final String myText;
  private SoftReference<PsiAnnotation> myParsedFromRepository;

  public PsiAnnotationStubImpl(StubElement<?> parent, @NotNull String text) {
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
    PsiAnnotation annotation = dereference(myParsedFromRepository);
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