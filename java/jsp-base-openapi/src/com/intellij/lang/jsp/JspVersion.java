// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.jsp;

/**
 * @author Dmitry Avdeev
 */
public interface JspVersion {

  JspVersion JSP_2_0 = new JspVersion() {

    @Override
    public String getNumber() {
      return "2.0";
    }

    @Override
    public boolean betterThan(JspVersion other) {
      return getNumber().compareTo(other.getNumber()) > 0;
    }
  };

  JspVersion JSP_2_1 = new JspVersion() {

    @Override
    public String getNumber() {
      return "2.1";
    }

    @Override
    public boolean betterThan(JspVersion other) {
      return getNumber().compareTo(other.getNumber()) > 0;
    }
  };

  JspVersion JSP_2_2 = new JspVersion() {

    @Override
    public String getNumber() {
      return "2.2";
    }

    @Override
    public boolean betterThan(JspVersion other) {
      return getNumber().compareTo(other.getNumber()) > 0;
    }
  };

  JspVersion JSP_2_3 = new JspVersion() {

    @Override
    public String getNumber() {
      return "2.3";
    }

    @Override
    public boolean betterThan(JspVersion other) {
      return getNumber().compareTo(other.getNumber()) > 0;
    }
  };

  JspVersion JSP_3_0 = new JspVersion() {

    @Override
    public String getNumber() {
      return "3.0";
    }

    @Override
    public boolean betterThan(JspVersion other) {
      return getNumber().compareTo(other.getNumber()) > 0;
    }
  };

  JspVersion MAX_VERSION = JSP_2_3;

  String getNumber();

  boolean betterThan(JspVersion other);
}
