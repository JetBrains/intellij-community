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
package com.intellij.diagnostic;

import com.intellij.openapi.components.NamedComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.apache.commons.codec.binary.Base64;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Aug 11, 2003
 * Time: 8:59:04 PM
 * To change this template use Options | File Templates.
 */
public class ErrorReportConfigurable implements JDOMExternalizable, NamedComponent {
  public String ITN_LOGIN = "";
  public String ITN_PASSWORD_CRYPT = "";
  public boolean KEEP_ITN_PASSWORD = false;

  public String EMAIL = "";

  public static ErrorReportConfigurable getInstance() {
    return ServiceManager.getService(ErrorReportConfigurable.class);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    if (! KEEP_ITN_PASSWORD)
      ITN_PASSWORD_CRYPT = "";
  }

  public void writeExternal(Element element) throws WriteExternalException {
    String itnPassword = ITN_PASSWORD_CRYPT;
    if (! KEEP_ITN_PASSWORD)
      ITN_PASSWORD_CRYPT = "";
    DefaultJDOMExternalizer.writeExternal(this, element);

    ITN_PASSWORD_CRYPT = itnPassword;
  }

  @NotNull
  public String getComponentName() {
    return "ErrorReportConfigurable";
  }

  public String getPlainItnPassword () {
    return new String(new Base64().decode(ErrorReportConfigurable.getInstance().ITN_PASSWORD_CRYPT.getBytes()));
  }

  public void setPlainItnPassword (String password) {
    ITN_PASSWORD_CRYPT = new String(new Base64().encode(password.getBytes()));
  }
}
