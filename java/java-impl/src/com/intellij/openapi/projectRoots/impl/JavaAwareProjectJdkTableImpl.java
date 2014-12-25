/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.util.SystemProperties;
import org.jdom.Element;

public class JavaAwareProjectJdkTableImpl extends ProjectJdkTableImpl {
  public static JavaAwareProjectJdkTableImpl getInstanceEx() {
    return (JavaAwareProjectJdkTableImpl)ServiceManager.getService(ProjectJdkTable.class);
  }

  private final JavaSdk myJavaSdk;
  private Sdk myInternalJdk;

  public JavaAwareProjectJdkTableImpl(final JavaSdk javaSdk) {
    myJavaSdk = javaSdk;
  }

  public Sdk getInternalJdk() {
    if (myInternalJdk == null) {
      final String jdkHome = SystemProperties.getJavaHome();
      final String versionName = ProjectBundle.message("sdk.java.name.template", SystemProperties.getJavaVersion());
      myInternalJdk = myJavaSdk.createJdk(versionName, jdkHome);
    }
    return myInternalJdk;
  }

  @Override
  public void removeJdk(final Sdk jdk) {
    super.removeJdk(jdk);
    if (jdk.equals(myInternalJdk)) {
      myInternalJdk = null;
    }
  }

  @Override
  public SdkTypeId getDefaultSdkType() {
    return myJavaSdk;
  }

  @Override
  public void loadState(final Element element) {
    myInternalJdk = null;
    try {
      super.loadState(element);
    }
    finally {
      getInternalJdk();
    }
  }

  @Override
  protected String getSdkTypeName(final String type) {
    return type != null ? type : JavaSdk.getInstance().getName();
  }
}
