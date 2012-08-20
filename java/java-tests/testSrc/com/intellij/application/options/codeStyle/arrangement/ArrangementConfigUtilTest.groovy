/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement

import org.jetbrains.annotations.NotNull
import org.junit.Test

import javax.swing.tree.DefaultMutableTreeNode

import static org.junit.Assert.assertEquals
/**
 * @author Denis Zhdanov
 * @since 8/17/12 1:12 PM
 */
class ArrangementConfigUtilTest {

  @Test
  void replace() {
    // Init.
    def one;
    def four;
    def initial = new TreeNodeBuilder().
      '0' {
        one = '1' {
          '2' {
            '3'()
            four = '4'()
            '5'()
          }
          '6'()
        }
      }
    
    // Modify.
    def replacement = new TreeNodeBuilder().
      '1' {
        '4'()
      }
    ArrangementConfigUtil.replace(one, four, replacement)
    
    // Check
    def expected = new TreeNodeBuilder().
      '0' {
        '1' {
          '2' {
            '3'()
          }
          '4'()
          '2' {
            '5'
          }
          '6'()
        }
      }
    // TODO den uncomment
    //assertNodesEqual(expected, initial)
  }
  
  private static void assertNodesEqual(@NotNull DefaultMutableTreeNode expected, @NotNull DefaultMutableTreeNode actual) {
    assertEquals(expected.userObject, actual.userObject)
    assertEquals(expected.childCount, actual.childCount)
    for (i in [0..<expected.childCount]) {
      assertNodesEqual(expected.getChildAt(i), actual.getChildAt(i))
    }
  }
}

public class TreeNodeBuilder extends BuilderSupport {

  @Override
  protected Object createNode(Object name) {
    def result = new DefaultMutableTreeNode(name)
    currentNode?.add(result)
    result
  }
  
  @NotNull
  DefaultMutableTreeNode getCurrentNode() {
    getCurrent() as DefaultMutableTreeNode
  }

  @Override protected void setParent(Object parent, Object child) { }
  @Override protected Object createNode(Object name, Object value) { throw new UnsupportedOperationException() }
  @Override protected Object createNode(Object name, Map attributes) { throw new UnsupportedOperationException() }
  @Override protected Object createNode(Object name, Map attributes, Object value) { throw new UnsupportedOperationException() }
}