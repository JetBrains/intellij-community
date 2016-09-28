/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import org.intellij.lang.annotations.MagicConstant;

import java.awt.*;
import javax.swing.*;

public class X {
  void f(JFrame frame) {
    frame.setDefaultCloseOperation(2);  // there is beanInfo in in JFrame.java, have to parse (but added to exceptions, so ok)
  }

  void f(Frame frame) {
    frame.setState(2);  // no beanInfo in Frame.java, no need to parse
  }
}
