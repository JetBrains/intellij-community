/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
/**
 * @author Denis Zhdanov
 * @since 7/6/11 6:52 PM 
 */
class BlockIndentOnPasteTest extends LightCodeInsightFixtureTestCase {

  void testJavaBlockDecreasedIndentOnTwoLinesPasting() {
    def before = '''\
class Test {
    void test() {
        if (true) {
            <caret>
        }
    }
}\
'''
    
    def toPaste =
'''\
                            foo();
                               foo();\
'''

    
    def expected = '''\
class Test {
    void test() {
        if (true) {
            foo();
               foo();
        }
    }
}\
'''
    doTest(before, toPaste, expected)
  }

  void testStringBeforeAnotherStringShouldNotIncreaseIndentOfTheFollowingString() {
    def before = '''\
class Test {
    void test() {
<caret>        int a = 100;
        int b = 200;
    }\
'''
    
    def toPaste = '''\
    int b = 200;
'''
    
    def expected = '''\
class Test {
    void test() {
        int b = 200;
        int a = 100;
        int b = 200;
    }\
'''
                                        
    doTest(before, toPaste, expected)
  }
  
  void testJavaComplexBlockWithDecreasedIndent() {
    def before = '''\
class Test {
    void test() {
        if (true) {
            i = 1;
        } else {
            i = 2;
        }
                              <caret>
    }
}\
'''

    def toPaste =
    '''\
        if (true) {
            i = 1;
        } else {
            i = 2;
        }\
'''

    def expected = '''\
class Test {
    void test() {
        if (true) {
            i = 1;
        } else {
            i = 2;
        }
        if (true) {
            i = 1;
        } else {
            i = 2;
        }
    }
}\
'''
    doTest(before, toPaste, expected)
  }
  
  void testJavaBlockIncreasedIndentOnTwoLinesPasting() {
    def before = '''\
class Test {
    void test() {
        if (true) {
            <caret>
        }
    }
}\
'''

    def toPaste =
    '''\
foo();
   foo();\
'''


    def expected = '''\
class Test {
    void test() {
        if (true) {
            foo();
               foo();
        }
    }
}\
'''
    doTest(before, toPaste, expected)
  }

  void testPastingBlockEndedByLineFeed() {
    def before = '''\
class Test {
    void test() {
        if (true) {
        <caret>}
    }
}\
'''

    def toPaste =
    '''\
int i = 1;
'''


    def expected = '''\
class Test {
    void test() {
        if (true) {
            int i = 1;
        }
    }
}\
'''
    doTest(before, toPaste, expected)
  }

  
  void testPasteAtZeroColumn() {
    def before = '''\
class Test {
    void test() {
     if (true) {
<caret>
     }
    }
}\
'''

    def toPaste =
    '''\
 foo();
  foo();\
'''


    def expected = '''\
class Test {
    void test() {
     if (true) {
         foo();
          foo();
     }
    }
}\
'''
    doTest(before, toPaste, expected)
  }

  void testPasteAtDocumentStartColumn() {
    def before = '<caret>'

    def toPaste =
    '''\
          class Test {
          }\
'''


    def expected = '''\
class Test {
}\
'''
    doTest(before, toPaste, expected)
  }
  
  void testBlockDecreasedIndentOnThreeLinesPasting() {
    def before = '''\
class Test {
    void test() {
        if (true) {
            <caret>
        }
    }
}\
'''

    def toPaste =
    '''\
              foo();
              foo();
                 foo();\
'''


    def expected = '''\
class Test {
    void test() {
        if (true) {
            foo();
            foo();
               foo();
        }
    }
}\
'''
    doTest(before, toPaste, expected)
  }

  void testBlockIncreasedIndentOnThreeLinesPasting() {
    def before = '''\
class Test {
    void test() {
        if (true) {
            <caret>
        }
    }
}\
'''

    def toPaste =
    '''\
foo();
 foo();
    foo();\
'''


    def expected = '''\
class Test {
    void test() {
        if (true) {
            foo();
             foo();
                foo();
        }
    }
}\
'''
    doTest(before, toPaste, expected)
  }

  void testBlockWithIndentOnFirstLine() {
    def before = '''\
class Test {
    void test() {
        if (true) {
            <caret>
        }
    }
}\
'''

    def toPaste =
    '''\
                 foo();
                    foo();
                  foo();\
'''


    def expected = '''\
class Test {
    void test() {
        if (true) {
            foo();
               foo();
             foo();
        }
    }
}\
'''
    doTest(before, toPaste, expected)
  }

  void testPasteAfterExistingSymbols() {
    def before = '''\
class Test {
    void test() {
        if (true) {<caret>
        }
    }
}\
'''

    def toPaste =
    '''\
// this is a comment
 foo();
  foo();
foo();\
'''


    def expected = '''\
class Test {
    void test() {
        if (true) {// this is a comment
         foo();
          foo();
        foo();
        }
    }
}\
'''
    doTest(before, toPaste, expected)
  }

  void testPasteAtZeroColumnAfterBlankLineWithWhiteSpaces() {
    def before = '''\
class Test {
    void test() {
        if (true) {
        }
    }
}
   
<caret>\
'''

    def toPaste =
    '''\
class Test {
    void test() {
        if (true) {
        }
    }
}\
'''


    def expected = '''\
class Test {
    void test() {
        if (true) {
        }
    }
}

class Test {
    void test() {
        if (true) {
        }
    }
}\
'''
    doTest(before, toPaste, expected)
  }

  void testPasteAtNonZeroColumnAfterBlankLineWithWhiteSpaces() {
    def before = '''\
class Test {
    void test() {
        if (true) {
        }
    }
}
   
    <caret>\
'''

    def toPaste =
    '''\
class Test {
    void test() {
        if (true) {
        }
    }
}\
'''


    def expected = '''\
class Test {
    void test() {
        if (true) {
        }
    }
}
   
class Test {
    void test() {
        if (true) {
        }
    }
}\
'''
    doTest(before, toPaste, expected)
  }

  void testPasteToNonEmptyStringTextWithTrailingLineFeed() {
    def before = '''\
class Test {
    void test() {
        foo(1, <caret>);
    }
}\
'''

    def toPaste1 =
    '''\
calculate(3, 4)
'''

    def expected = '''\
class Test {
    void test() {
        foo(1, calculate(3, 4)
        );
    }
}\
'''
    doTest(before, toPaste1, expected)

    def toPaste2 =
    '''\
calculate(3, 4)
    '''
    doTest(before, toPaste2, expected)
  }

  void testPasteTextThatStartsWithLineFeedAfterNonEmptyLine() {
    def before = '''\
class Test {
    void test() {
        foo(1);
    }<caret>
}\
'''

    def toPaste1 =
      '''
    void test() {
        foo(1);
    }\
'''

    def expected1 = '''\
class Test {
    void test() {
        foo(1);
    }
    void test() {
        foo(1);
    }
}\
'''
    doTest(before, toPaste1, expected1)

    def toPaste2 =
      '''


    void test() {
        foo(1);
    }\
'''

    def expected2 = '''\
class Test {
    void test() {
        foo(1);
    }


    void test() {
        foo(1);
    }
}\
'''
    doTest(before, toPaste2, expected2)
  }

  void testPasteTextThatStartsWithLineFeedToNewLine() {
    def before = '''\
class Test {
    void test(int i) {
        if (i > 0) {<caret>
        }
    }
}\
'''

    def toPaste1 =
      '''
    if (i > 2) {
    }\
'''

    def expected1 = '''\
class Test {
    void test(int i) {
        if (i > 0) {
            if (i > 2) {
            }
        }
    }
}\
'''
    doTest(before, toPaste1, expected1)

    def toPaste2 =
      '''


    if (i > 2) {
    }\
'''

    def expected2 = '''\
class Test {
    void test(int i) {
        if (i > 0) {


            if (i > 2) {
            }
        }
    }
}\
'''
    doTest(before, toPaste2, expected2)
  }
  
  void testPasteMultilineTextThatStartsWithLineFeedToNewLineWithBigCaretIndent() {
      def before = '''\
  class Test {
      void test(int i) {
          if (i > 0) {
                   <caret>
          }
      }
  }\
'''
  
      def toPaste = '''
                       test(1);
                       test(1);\
'''
  
      def expected = '''\
  class Test {
      void test(int i) {
          if (i > 0) {
                   
              test(1);
              test(1);
          }
      }
  }\
'''
      doTest(before, toPaste, expected)
    
    
    def toPaste2 = '''
     test(1);
     test(1);\
'''
    doTest(before, toPaste2, expected)
  } 
  
  void testPlainTextPaste() {
    def before = '''\
  line1
  line2
     <caret>\
'''

    def toPaste =
    '''\
line to paste #1
     line to paste #2\
'''


    def expected = '''\
  line1
  line2
     line to paste #1
          line to paste #2\
'''
    doTest(before, toPaste, expected, StdFileTypes.PLAIN_TEXT)
  }
  
  void "test plain text when pasted string ends by line feed"() {
    def before = '''\
  line1
  line2
  <caret>
'''

    def toPaste =
    '''\
line to paste #1
line to paste #2
'''

    def expected = '''\
  line1
  line2
  line to paste #1
  line to paste #2

'''
    doTest(before, toPaste, expected, StdFileTypes.PLAIN_TEXT)
  }

  void "test plain text when caret is after selection"() {
    def before = '''\
<selection>  line1
</selection><caret>\
'''

    def toPaste =
    '''\
line to paste #1
line to paste #2
'''


    def expected = '''\
  line1
line to paste #1
line to paste #2
'''
    doTest(before, toPaste, expected, StdFileTypes.PLAIN_TEXT)
  }

  void testPlainTextThatStartsByLineFeed() {
    def before = '''\
line 1
  # item1<caret>
'''

    def toPaste1 =
      '''
  # item2\
'''
    
    def expected1 = '''\
line 1
  # item1
  # item2
'''
    doTest(before, toPaste1, expected1, StdFileTypes.PLAIN_TEXT)

    def toPaste2 =
      '''


  # item2\
'''

    def expected2 = '''\
line 1
  # item1


  # item2
'''
    doTest(before, toPaste2, expected2, StdFileTypes.PLAIN_TEXT)
  }
  
  void "test formatter-based paste that starts with white space"() {
    def before = '''\
class Test {
    int i;
    int j;

    void test() {
        <caret>
    }
}
'''
    
    def toPaste = '''\
    int i;
    int j;\
'''
    
    def expected = '''\
class Test {
    int i;
    int j;

    void test() {
        int i;
        int j;
    }
}
'''
    doTest(before, toPaste, expected)
  }
  
  def doTest(String before, toPaste, expected, FileType fileType = StdFileTypes.JAVA) {
    myFixture.configureByText(fileType, before)

    def settings = CodeInsightSettings.getInstance()
    def old = settings.REFORMAT_ON_PASTE
    settings.REFORMAT_ON_PASTE = CodeInsightSettings.INDENT_BLOCK
    try {
      def offset = myFixture.editor.caretModel.offset
      def column = myFixture.editor.caretModel.logicalPosition.column
      com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction project, {
        myFixture.editor.document.insertString(offset, toPaste)
        PasteHandler.indentBlock(project, myFixture.editor, offset, offset + toPaste.length(), column)
      }
    }
    finally {
      settings.REFORMAT_ON_PASTE = old
    }
    myFixture.editor.selectionModel.removeSelection()
    myFixture.checkResult(expected)
  }
}
