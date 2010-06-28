package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.InheritedJdkOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.testFramework.ModuleTestCase;
import junit.framework.Assert;

/**
 * @author dsl
 */
public class InheritedJdkTest extends ModuleTestCase {
  protected void setUpJdk() {
  }

  public void test1() throws Exception {
    final Sdk jdk = JavaSdkImpl.getMockJdk("java 1.4");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ProjectJdkTable.getInstance().addJdk(jdk);
      }
    });
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ProjectRootManagerEx rootManagerEx = ProjectRootManagerEx.getInstanceEx(myProject);
        rootManagerEx.setProjectJdkName(jdk.getName());
        final ModifiableRootModel rootModel = rootManager.getModifiableModel();
        rootModel.inheritSdk();
        rootModel.commit();
      }
    });

    assertTrue("JDK is inherited after explicit inheritSdk()", rootManager.isSdkInherited());
    assertTrue("Correct jdk inherited", jdk.equals(rootManager.getSdk()));

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ModifiableRootModel rootModel = rootManager.getModifiableModel();
        rootModel.setSdk(null);
        rootModel.commit();
      }
    });

    assertFalse("JDK is not inherited after setJdk(null)", rootManager.isSdkInherited());
    assertTrue("No JDK assigned", null == rootManager.getSdk());

    final Sdk jdk1 = JavaSdkImpl.getMockJdk("jjj");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ProjectJdkTable.getInstance().addJdk(jdk1);
      }
    });
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ModifiableRootModel rootModel = rootManager.getModifiableModel();
        rootModel.setSdk(jdk1);
        rootModel.commit();
      }
    });

    assertFalse("JDK is not inherited after setJdk(jdk1)", rootManager.isSdkInherited());
    assertTrue("jdk1 is assigned", jdk1.equals(rootManager.getSdk()));
  }

  public void test2() throws Exception {
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
    final ModifiableRootModel rootModel = rootManager.getModifiableModel();
    rootModel.inheritSdk();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        rootModel.commit();
      }
    });

    assertTrue("JDK is inherited after inheritSdk()", rootManager.isSdkInherited());
    assertTrue("No JDK assigned", null == rootManager.getSdk());

    final Sdk mockJdk = JavaSdkImpl.getMockJdk("mock 1.4");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ProjectJdkTable.getInstance().addJdk(mockJdk);
      }
    });
    final ProjectRootManagerEx projectRootManager = ProjectRootManagerEx.getInstanceEx(myProject);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        projectRootManager.setProjectJdk(mockJdk);
      }
    });

    assertTrue(rootManager.isSdkInherited());
    assertTrue("mockJdk inherited", mockJdk.equals(rootManager.getSdk()));

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        projectRootManager.setProjectJdkName("jdk1");
      }
    });

    assertTrue(rootManager.isSdkInherited());
    Assert.assertEquals("Correct non-existing JDK inherited", "jdk1",
                 rootManager.processOrder(new RootPolicy<String>() {
                   public String visitInheritedJdkOrderEntry(InheritedJdkOrderEntry inheritedJdkOrderEntry, String s) {
                     return inheritedJdkOrderEntry.getJdkName();
                   }
                 }, null));
    assertNull("Non-existing JDK", rootManager.getSdk());

    final Sdk jdk1 = JavaSdkImpl.getMockJdk("jdk1");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ProjectJdkTable.getInstance().addJdk(jdk1);
      }
    });

    assertTrue(rootManager.isSdkInherited());
    assertNotNull("JDK appeared", rootManager.getSdk());
    assertTrue("jdk1 found", jdk1.equals(rootManager.getSdk()));
  }
}
