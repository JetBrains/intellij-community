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

import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition
import gnu.trove.TIntIntHashMap
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.junit.Test

import javax.swing.tree.DefaultTreeModel

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
one =    '1' {
          '2' {
            '3'()
four =      '4'()
            '5'()}
          '6'()}
      }
    
    // Modify.
    def replacement = new TreeNodeBuilder().
      '1' {
        '4'()
      }
    def rowMappings = doReplace(initial, one, four, replacement)
    
    // Check
    def expected = new TreeNodeBuilder().
      '0' {
        '1' {
          '2' {
            '3'()}
          '4'()
          '2' {
            '5'()}
          '6'()}
      }
    assertNodesEqual(expected, initial)
    checkRowMappings([5 : 6, 6 : 7], rowMappings)
  }

  @Test
  void replaceWithMergeToNodeAbove() {
        // Init.
    def from;
    def to;
    def initial = new TreeNodeBuilder().
      '0' {
        '1'() {
          '2'()
          '3'()}
from =  '4' {
to =      '5'()
          '6'()}
      }
    
    // Modify.
    def replacement = new TreeNodeBuilder().
      '1' {
        '5'()
      }
    def rowMappings = doReplace(initial, from, to, replacement)
    
    // Check.
    def expected = new TreeNodeBuilder().
      '0' {
        '1' {
          '2'()
          '3'()
          '5'()}
        '4' {
          '6'()}
      }
    assertNodesEqual(expected, initial)
    checkRowMappings([:], rowMappings)
  }
  
  @Test
  void replaceWithTwoLevelMergeToNodeAbove() {
        // Init.
    def node;
    def initial = new TreeNodeBuilder().
      '0' {
        '1'() {
          '2'()
          '3'()}
node =  '4'()
        '5'() {
          '6'()}        
      }
    
    // Modify.
    def replacement = new TreeNodeBuilder().
      '1' {
        '4'()
      }
    def rowMappings = doReplace(initial, node, node, replacement)
    
    // Check.
    def expected = new TreeNodeBuilder().
      '0' {
        '1' {
          '2'()
          '3'()
          '4'()}
        '5' {
          '6'()}
      }
    assertNodesEqual(expected, initial)
    checkRowMappings([:], rowMappings)
  }
  
    @Test
  void replaceWithTwoLevelMergeToNodeBelow() {
        // Init.
    def from;
    def to;
    def initial = new TreeNodeBuilder().
      '0' {
from =  '1'() {
          '2'()
to =      '3'()}
        '4'() {
          '5'() }        
      }
    
    // Modify.
    def replacement = new TreeNodeBuilder().
      '4' {
        '3'()
      }
    def rowMappings = doReplace(initial, from, to, replacement)
    
    // Check.
    def expected = new TreeNodeBuilder().
      '0' {
        '1' {
          '2'()}
        '4' {
          '3'()
          '5'()}
      }
    assertNodesEqual(expected, initial)
    checkRowMappings([:], rowMappings)
  }
  
  @Test
  void addWithoutMergeAbove() {
    def initial = new TreeNodeBuilder().
      '0' {
        '1'()
        '2' {
          '3' {
            '4'()
          }
        }
        '5'()
      }
    
    def toAdd = new TreeNodeBuilder().
      '2' {
        '3' {
          '6'()
        }
      }

    def expected = new TreeNodeBuilder().
       '0' {
         '2' {
           '3' {
             '6'()
           }
         }
         '1'()
         '2' {
           '3' {
             '4'()
           }
         }
         '5'()
       }
    
    doInsert(initial, 0, toAdd)
    assertNodesEqual(expected, initial)
  }

  @Test
  void addWithMergeAbove() {
    def initial = new TreeNodeBuilder().
      '0' {
        '1'()
        '2' {
          '3' {
            '4'()
          }
        }
        '5'()
      }
    
    def toAdd = new TreeNodeBuilder().
      '2' {
        '3' {
          '6'()
        }
      }

    def expected = new TreeNodeBuilder().
       '0' {
         '1'()
         '2' {
           '3' {
             '6'()
             '4'()
           }
         }
         '5'()
       }

    doInsert(initial, 1, toAdd)
    assertNodesEqual(expected, initial)
  }

  @Test
  void addWithMergeBelow() {
    def initial = new TreeNodeBuilder().
            '0' {
              '1'()
              '2' {
                '3' {
                  '4'()
                }
              }
              '5'()
            }

    def toAdd = new TreeNodeBuilder().
            '2' {
              '3' {
                '6'()
              }
            }

    def expected = new TreeNodeBuilder().
            '0' {
              '1'()
              '2' {
                '3' {
                  '4'()
                  '6'()
                }
              }
              '5'()
            }

    doInsert(initial, 2, toAdd)
    assertNodesEqual(expected, initial)
  }
  
  @Test
  void addWithoutMergeBelow() {
    def initial = new TreeNodeBuilder().
      '0' {
        '1'()
        '2' {
          '3' {
            '4'()
          }
        }
        '5'()
      }
    
    def toAdd = new TreeNodeBuilder().
      '2' {
        '3' {
          '6'()
        }
      }

    def expected = new TreeNodeBuilder().
       '0' {
         '1'()
         '2' {
           '3' {
             '4'()
           }
         }
         '5'()
         '2' {
           '3' {
             '6'()
           }
         }
       }

    doInsert(initial, 3, toAdd)
    assertNodesEqual(expected, initial)
  }
  
  private static def doReplace(initial, from, to, replacement) {
    ArrangementConfigUtil.replace(from, to, replacement, new DefaultTreeModel(initial), true)
  }

  private static def doInsert(parent, i, child) {
    ArrangementConfigUtil.insert(parent, i, child, new DefaultTreeModel(ArrangementConfigUtil.getRoot(parent)))
  }
  
  private static void assertNodesEqual(@NotNull ArrangementTreeNode expected, @NotNull ArrangementTreeNode actual) {
    assertEquals(expected.userObject, actual.userObject)
    assertEquals(expected.childCount, actual.childCount)
    for (i in 0..<expected.childCount) {
      assertNodesEqual(expected.getChildAt(i), actual.getChildAt(i))
    }
  }

  private static void checkRowMappings(@NotNull Map<Integer, Integer> expected, @NotNull TIntIntHashMap actual) {
    assertEquals(expected.size(), actual.size())
    expected.each {key, value -> assertEquals(value, actual.get(key)) }
  }
}

public class TreeNodeBuilder extends BuilderSupport {

  @Override
  protected Object createNode(Object name) {
    def result = new ArrangementTreeNode(new ArrangementAtomMatchCondition(ArrangementSettingType.MODIFIER, name))
    currentNode?.add(result)
    result
  }
  
  @Nullable
  ArrangementTreeNode getCurrentNode() {
    getCurrent() as ArrangementTreeNode
  }

  @Override protected void setParent(Object parent, Object child) { }
  @Override protected Object createNode(Object name, Object value) { throw new UnsupportedOperationException() }
  @Override protected Object createNode(Object name, Map attributes) { throw new UnsupportedOperationException() }
  @Override protected Object createNode(Object name, Map attributes, Object value) { throw new UnsupportedOperationException() }
}