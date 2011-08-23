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
package com.intellij.codeInsight.editorActions

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.testFramework.TestFileType

/** 
 * @author Denis Zhdanov
 * @since 7/6/11 6:52 PM 
 */
class BlockIndentOnPasteTest extends LightPlatformCodeInsightTestCase {

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
  
  def testPlainTextPaste() {
    def before = '''\
  line1
  line2
     <caret>\
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
          line to paste #2\
'''
    doTest(before, toPaste, expected, TestFileType.TEXT)
  }
  
  def testPlainTextPasteWithCompleteReplacement() {
    def before = '''\
<selection>  line1
  line2</selection>\
'''

    def toPaste =
    '''\
line to paste #1
line to paste #2
'''


    def expected = '''\
line to paste #1
line to paste #2
'''
    doTest(before, toPaste, expected, TestFileType.TEXT)
  }

  def testPlainTextMultilinePasteWithCaretAfterSelection() {
    def before = '''\
<selection>  line1</selection><caret>\
'''

    def toPaste =
    '''\
line to paste #1
line to paste #2
'''


    def expected = '''\
line to paste #1
line to paste #2
'''
    doTest(before, toPaste, expected, TestFileType.TEXT)
  }
  
  def doTest(before, toPaste, expected, fileType = TestFileType.JAVA) {
    configureFromFileText("${getTestName(false)}.$fileType.extension", before)

    def settings = CodeInsightSettings.getInstance()
    def old = settings.REFORMAT_ON_PASTE
    settings.REFORMAT_ON_PASTE = CodeInsightSettings.INDENT_BLOCK
    try {
      def offset = editor.caretModel.offset
      def column = editor.caretModel.logicalPosition.column
      myEditor.document.insertString(offset, toPaste)
      PasteHandler.indentBlock(project, editor, offset, offset + toPaste.length(), column)
    }
    finally {
      settings.REFORMAT_ON_PASTE = old
    }
    checkResultByText(expected)
  }
}
