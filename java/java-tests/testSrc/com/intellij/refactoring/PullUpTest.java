/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.MoveMemberListener;
import com.intellij.refactoring.memberPullUp.PullUpHelper;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 * @author ven
 */
public class PullUpTest extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/refactoring/pullUp/";


  public void testQualifiedThis() throws Exception {
    doTest(new MemberDescriptor ("Inner", PsiClass.class));
  }

  public void testQualifiedSuper() throws Exception {
    doTest(new MemberDescriptor ("Inner", PsiClass.class));
  }

  public void testQualifiedReference() throws Exception {     // IDEADEV-25008
    doTest(new MemberDescriptor ("x", PsiField.class),
           new MemberDescriptor ("getX", PsiMethod.class),
           new MemberDescriptor ("setX", PsiMethod.class));

  }
  
  public void testPullUpAndAbstractize() throws Exception {
    doTest(new MemberDescriptor("a", PsiMethod.class),
           new MemberDescriptor("b", PsiMethod.class, true));
  }

  public void testTryCatchFieldInitializer() throws Exception {
    doTest(new MemberDescriptor("field", PsiField.class));
  }

  public void testIfFieldInitializationWithNonMovedField() throws Exception {
    doTest(new MemberDescriptor("f", PsiField.class));
  }

  public void testIfFieldMovedInitialization() throws Exception {
    doTest(new MemberDescriptor("f", PsiField.class));
  }

  public void testMultipleConstructorsFieldInitialization() throws Exception {
    doTest(new MemberDescriptor("f", PsiField.class));
  }

  public void testMultipleConstructorsFieldInitializationNoGood() throws Exception {
    doTest(new MemberDescriptor("f", PsiField.class));
  }


  public void testRemoveOverride() throws Exception {
    doTest(new MemberDescriptor ("get", PsiMethod.class));
  }

  public void testTypeParamErasure() throws Exception {
    doTest(new MemberDescriptor("f", PsiField.class));
  }

  public void testTypeParamSubst() throws Exception {
    doTest(new MemberDescriptor("f", PsiField.class));
  }

  public void testTypeArgument() throws Exception {
    doTest(new MemberDescriptor("f", PsiField.class));
  }

  private void doTest(MemberDescriptor... membersToFind) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement elementAt = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    final PsiClass sourceClass = PsiTreeUtil.getParentOfType(elementAt, PsiClass.class);
    assertNotNull(sourceClass);

    PsiClass targetClass = sourceClass.getSuperClass();
    if (!targetClass.isWritable()) {
      final PsiClass[] interfaces = sourceClass.getInterfaces();
      assertTrue(interfaces.length == 1);
      assertTrue(interfaces[0].isWritable());
      targetClass = interfaces[0];
    }
    MemberInfo[] infos = findMembers(sourceClass, membersToFind);

    final int[] countMoved = new int[] {0};
    final MoveMemberListener listener = new MoveMemberListener() {
      public void memberMoved(PsiClass aClass, PsiMember member) {
        assertEquals(sourceClass, aClass);
        countMoved[0]++;
      }
    };
    JavaRefactoringListenerManager.getInstance(getProject()).addMoveMembersListener(listener);
    final PullUpHelper helper = new PullUpHelper(sourceClass, targetClass, infos, new DocCommentPolicy(DocCommentPolicy.ASIS));
    helper.run();
    JavaRefactoringListenerManager.getInstance(getProject()).removeMoveMembersListener(listener);
    assertEquals(countMoved[0], membersToFind.length);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public static MemberInfo[] findMembers(final PsiClass sourceClass, final MemberDescriptor... membersToFind) {
    MemberInfo[] infos = new MemberInfo[membersToFind.length];
    for (int i = 0; i < membersToFind.length; i++) {
      final Class<? extends PsiMember> clazz = membersToFind[i].myClass;
      final String name = membersToFind[i].myName;
      PsiMember member = null;
      boolean overrides = false;
      PsiReferenceList refList = null;
      if (PsiClass.class.isAssignableFrom(clazz)) {
        member = sourceClass.findInnerClassByName(name, false);
        if (member == null) {
          final PsiClass[] supers = sourceClass.getSupers();
          for (PsiClass superTypeClass : supers) {
            if (superTypeClass.getName().equals(name)) {
              member = superTypeClass;
              overrides = true;
              refList = superTypeClass.isInterface() ? sourceClass.getImplementsList() : sourceClass.getExtendsList();
              break;
            }
          }
        }

      } else if (PsiMethod.class.isAssignableFrom(clazz)) {
        final PsiMethod[] methods = sourceClass.findMethodsByName(name, false);
        assertEquals(1, methods.length);
        member = methods[0];
      } else if (PsiField.class.isAssignableFrom(clazz)) {
        member = sourceClass.findFieldByName(name, false);
      }

      assertNotNull(member);
      infos[i] = new MemberInfo(member, overrides, refList);
      infos[i].setToAbstract(membersToFind[i].myAbstract);
    }
    return infos;
  }

  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk15("50");
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
  
  public static class MemberDescriptor {
    private String myName;
    private Class<? extends PsiMember> myClass;
    private boolean myAbstract;

    public MemberDescriptor(String name, Class<? extends PsiMember> aClass, boolean isAbstract) {
      myName = name;
      myClass = aClass;
      myAbstract = isAbstract;
    }


    public MemberDescriptor(String name, Class<? extends PsiMember> aClass) {
      this(name, aClass, false);
    }
  }
}
