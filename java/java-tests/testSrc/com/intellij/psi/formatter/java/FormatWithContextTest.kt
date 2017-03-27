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
package com.intellij.psi.formatter.java

import com.intellij.formatting.FormatterTestUtils
import com.intellij.openapi.util.TextRange


class FormatWithContextTest : AbstractJavaFormatterTest() {

  fun check(before: String, after: String) {
    doTextTest(FormatterTestUtils.Action.REFORMAT_WITH_INSERTED_LINE_CONTEXT, before, after)
  }
  
  fun `test if block`() {
    myLineRange = TextRange(3, 3)
    check(
        """
class X {
    void test() {
       if (1 > 2) {
        int a = 2;
        int b = 3;
        }
    }
}
""",
        """
class X {
    void test() {
        if (1 > 2) {
            int a = 2;
            int b = 3;
        }
    }
}
"""
    )
  }

  fun `test for block`() {
    myLineRange = TextRange(3, 3)
    check(
        """
class X {
    void test() {
       for (int i = 0; i < 3; i++) {
        int a = 2;
        int b = 3;
        }
    }
}
""",
        """
class X {
    void test() {
        for (int i = 0; i < 3; i++) {
            int a = 2;
            int b = 3;
        }
    }
}
"""
    )
  }
  
  fun `test while block`() {
    myLineRange = TextRange(3, 3)
    check(
        """
class X {
    void test() {
       while (true) {
        int a = 2;
        int b = 3;
        }
    }
}
""",
        """
class X {
    void test() {
        while (true) {
            int a = 2;
            int b = 3;
        }
    }
}
"""
    )
  }

  
  fun `test inserted closing brace`() {
    myLineRange = TextRange(6, 6)
    check(
        """
class X {
    void test() {
       while (true) {
        int a = 2;
        int b = 3;
        }
    }
}
""",
        """
class X {
    void test() {
        while (true) {
            int a = 2;
            int b = 3;
        }
    }
}
"""
    )
  }


}