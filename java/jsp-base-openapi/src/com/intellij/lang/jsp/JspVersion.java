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

package com.intellij.lang.jsp;

/**
 * @author Dmitry Avdeev
 */
public interface JspVersion {

  JspVersion JSP_2_0 = new JspVersion() {

    public String getNumber() {
      return "2.0";
    }

    public boolean betterThan(JspVersion other) {
      return getNumber().compareTo(other.getNumber()) > 0;
    }
  };

  JspVersion JSP_2_1 = new JspVersion() {

    public String getNumber() {
      return "2.1";
    }

    public boolean betterThan(JspVersion other) {
      return getNumber().compareTo(other.getNumber()) > 0;
    }
  };

  JspVersion JSP_2_2 = new JspVersion() {

    public String getNumber() {
      return "2.2";
    }

    public boolean betterThan(JspVersion other) {
      return getNumber().compareTo(other.getNumber()) > 0;
    }
  };

  JspVersion JSP_2_3 = new JspVersion() {

    public String getNumber() {
      return "2.3";
    }

    public boolean betterThan(JspVersion other) {
      return getNumber().compareTo(other.getNumber()) > 0;
    }
  };

  JspVersion MAX_VERSION = JSP_2_3;

  String getNumber();

  boolean betterThan(JspVersion other);

}
