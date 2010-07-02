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
    final Sdk jdk = JavaSdkImpl.getMockJdk17("java 1.4");
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
    assertEquals("Correct jdk inherited", jdk, rootManager.getSdk());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ModifiableRootModel rootModel = rootManager.getModifiableModel();
        rootModel.setSdk(null);
        rootModel.commit();
      }
    });

    assertFalse("JDK is not inherited after setJdk(null)", rootManager.isSdkInherited());
    assertNull("No JDK assigned", rootManager.getSdk());

    final Sdk jdk1 = JavaSdkImpl.getMockJdk17("jjj");
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
    assertEquals("jdk1 is assigned", jdk1, rootManager.getSdk());
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
    assertNull("No JDK assigned", rootManager.getSdk());

    final Sdk mockJdk = JavaSdkImpl.getMockJdk17("mock 1.4");
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
    assertEquals("mockJdk inherited", mockJdk, rootManager.getSdk());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        projectRootManager.setProjectJdkName("jdk1");
      }
    });

    assertTrue(rootManager.isSdkInherited());
    Assert.assertEquals("Correct non-existing JDK inherited", "jdk1",
                 rootManager.orderEntries().process(new RootPolicy<String>() {
                   public String visitInheritedJdkOrderEntry(InheritedJdkOrderEntry inheritedJdkOrderEntry, String s) {
                     return inheritedJdkOrderEntry.getJdkName();
                   }
                 }, null));
    assertNull("Non-existing JDK", rootManager.getSdk());

    final Sdk jdk1 = JavaSdkImpl.getMockJdk17("jdk1");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ProjectJdkTable.getInstance().addJdk(jdk1);
      }
    });

    assertTrue(rootManager.isSdkInherited());
    assertNotNull("JDK appeared", rootManager.getSdk());
    assertEquals("jdk1 found", jdk1, rootManager.getSdk());
  }
}
