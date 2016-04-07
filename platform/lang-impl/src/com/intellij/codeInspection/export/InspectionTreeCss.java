/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.export;

/**
 * @author Dmitry Batkovich
 */
class InspectionTreeCss {
  static final String CSS_TEXT;

  static {
    CSS_TEXT = "html {\n" +
               "    font-family: \"Helvetica Neue\", Helvetica, Arial, \"Lucida Grande\", sans-serif;\n" +
               "}\n" +
               "body {\n" +
               "    color: #4C4C4C;\n" +
               "    margin: 60px 60px 0 60px;\n" +
               "}\n" +
               "p {\n" +
               "    font-size: 1em;\n" +
               "    margin: 0 0 1em 0;\n" +
               "}\n" +
               ".grayout {\n" +
               "    opacity: 0.6;\n" +
               "}\n" +
               "body, input, select, textarea, th, td {\n" +
               "    font-size: 1em;\n" +
               "}\n" +
               "li {\n" +
               "    position: relative;\n" +
               "    list-style: none;\n" +
               "}\n" +
               "label:hover {\n" +
               "    color: #0C479D;\n" +
               "    text-decoration: underline;\n" +
               "}\n" +
               "li::before {\n" +
               "    content: \"\\23FA\";\n" +
               "    margin: 0;\n" +
               "}\n" +
               "li input {\n" +
               "    position: absolute;\n" +
               "    left: 0;\n" +
               "    margin-left: 0;\n" +
               "    background: none;\n" +
               "    opacity: 0;\n" +
               "    z-index: 2;\n" +
               "    cursor: pointer;\n" +
               "    height: 1em;\n" +
               "    width: 1em;\n" +
               "    top: 0;\n" +
               "    content: \"some text>\";\n" +
               "}\n" +
               "li input + ol {\n" +
               "    margin: -15px 0 0 -44px;\n" +
               "    height: 1em;\n" +
               "}\n" +
               "li input + ol > li {\n" +
               "    display: none;\n" +
               "    margin-left: -14px !important;\n" +
               "    padding-left: 1px;\n" +
               "}\n" +
               "li label {\n" +
               "    padding-left: 5px;\n" +
               "}\n" +
               "li input:checked + ol {\n" +
               "    margin: -20px 0 0 -44px;\n" +
               "    padding: 25px 0 0 80px;\n" +
               "    height: auto;\n" +
               "}\n" +
               "li input:checked + ol > li {\n" +
               "    display: block;\n" +
               "    margin: 0 0 2px;\n" +
               "}\n" +
               "li input:checked + ol > li:last-child {\n" +
               "    margin: 0 0 1px;\n" +
               "}";
  }
}
