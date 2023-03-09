// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.codeInsight.javadoc.SnippetMarkup;
import com.intellij.codeInsight.javadoc.SnippetMarkup.ErrorMarkup;
import com.intellij.codeInsight.javadoc.SnippetMarkup.LocationMarkupNode;
import com.intellij.codeInsight.javadoc.SnippetMarkup.PlainText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class SnippetMarkupTest {
  @Test
  public void plainText() {
    testParsing(
      """
        Hello!!!
        World!!!""", """
        PlainText[range=(0,8), content=Hello!!!]
        PlainText[range=(9,17), content=World!!!]""");
  }

  @Test
  public void someMarkup() {
    testParsing(
      """
        public class X {
          // @start region=test
          void main() {
            System.out.println("Hello"); // @replace substring=Hello replacement=...
            // @highlight substring=Hello :
            System.out.println("Hello");
            // @highlight region:
            System.out.println("Hello1");
            System.out.println("Hello2"); // @link regex=H\\w+ target=Hello type=linkplain
            // @end
          }
          // @end
        }
        """, """
        PlainText[range=(0,16), content=public class X {]
        StartRegion[range=(23,40), region=test]
        PlainText[range=(41,56), content=  void main() {]
        Replace[range=(94,133), substring=Hello, regex=null, region=null, replacement=...]
        PlainText[range=(57,90), content=    System.out.println("Hello"); ]
        Highlight[range=(142,169), substring=Hello, regex=null, region=null, type=HIGHLIGHTED]
        PlainText[range=(170,202), content=    System.out.println("Hello");]
        Highlight[range=(211,228), substring=null, regex=null, region=, type=HIGHLIGHTED]
        PlainText[range=(229,262), content=    System.out.println("Hello1");]
        Link[range=(301,344), substring=null, regex=H\\w+, region=null, target=Hello, linkType=LINKPLAIN]
        PlainText[range=(263,297), content=    System.out.println("Hello2"); ]
        EndRegion[range=(353,356), region=null]
        PlainText[range=(357,360), content=  }]
        EndRegion[range=(367,370), region=null]
        PlainText[range=(371,372), content=}]
        PlainText[range=(373,373), content=]""");
  }

  @Test
  public void errors() {
    testParsing(
      """
        public class X {
          // @start
          void main() {
            System.out.println("Hello"); // @replace substring="Hello" substring2='GoodBye'
            // @highlight type=underline
            System.out.println("Hello"); // @link type=pfff
          }
          // @end test
        }
        """, """
        PlainText[range=(0,16), content=public class X {]
        ErrorMarkup[range=(23,28), message=@start: missing 'region' attribute]
        PlainText[range=(29,44), content=  void main() {]
        Replace[range=(82,128), substring=Hello, regex=null, region=null, replacement=]
        ErrorMarkup[range=(108,128), message=Unsupported attribute: 'substring2']
        ErrorMarkup[range=(82,128), message=@replace: missing 'replacement' attribute]
        PlainText[range=(45,78), content=    System.out.println("Hello"); ]
        Highlight[range=(137,161), substring=null, regex=null, region=null, type=HIGHLIGHTED]
        ErrorMarkup[range=(147,161), message=Unknown type 'underline'; only 'bold', 'italic', and 'highlighted' are supported]
        Link[range=(199,213), substring=null, regex=null, region=null, target=, linkType=LINK]
        ErrorMarkup[range=(199,213), message=@link: missing 'target' attribute]
        ErrorMarkup[range=(204,213), message=Unknown type 'pfff'; only 'link' and 'linkplain' are supported]
        PlainText[range=(162,195), content=    System.out.println("Hello"); ]
        PlainText[range=(214,217), content=  }]
        EndRegion[range=(224,232), region=null]
        ErrorMarkup[range=(228,232), message=Unsupported attribute: 'test']
        PlainText[range=(233,234), content=}]
        PlainText[range=(235,235), content=]""");
  }
  
  @Test
  public void visitorSimple() {
    String text = """
      public class Hello {
        // @start region=main
        public static void main(String[] args) {
          code: // @replace regex='code:' replacement="..."
          // @highlight substring="Hello"
          System.out.println("Hello");
          // @highlight region substring="idx"
          for(int idx=0; idx<10; idx++) {
            System.out.println(idx);
          }
          // @end
        }
        // @end
      }
      """;
    testParsing(text, """
      PlainText[range=(0,20), content=public class Hello {]
      StartRegion[range=(27,44), region=main]
      PlainText[range=(45,87), content=  public static void main(String[] args) {]
      Replace[range=(102,141), substring=null, regex=code:, region=null, replacement=...]
      PlainText[range=(88,98), content=    code: ]
      Highlight[range=(150,177), substring=Hello, regex=null, region=null, type=HIGHLIGHTED]
      PlainText[range=(178,210), content=    System.out.println("Hello");]
      Highlight[range=(219,251), substring=idx, regex=null, region=, type=HIGHLIGHTED]
      PlainText[range=(252,287), content=    for(int idx=0; idx<10; idx++) {]
      PlainText[range=(288,318), content=      System.out.println(idx);]
      PlainText[range=(319,324), content=    }]
      EndRegion[range=(333,336), region=null]
      PlainText[range=(337,340), content=  }]
      EndRegion[range=(347,350), region=null]
      PlainText[range=(351,352), content=}]
      PlainText[range=(353,353), content=]""");
    testVisitor(text, null, """
      public class Hello {
        public static void main(String[] args) {
          code:  // [Replace[range=(102,141), substring=null, regex=code:, region=null, replacement=...]]
          System.out.println("Hello"); // [Highlight[range=(150,177), substring=Hello, regex=null, region=null, type=HIGHLIGHTED]]
          for(int idx=0; idx<10; idx++) { // [Highlight[range=(219,251), substring=idx, regex=null, region=, type=HIGHLIGHTED]]
            System.out.println(idx); // [Highlight[range=(219,251), substring=idx, regex=null, region=, type=HIGHLIGHTED]]
          } // [Highlight[range=(219,251), substring=idx, regex=null, region=, type=HIGHLIGHTED]]
        }
      }
      
      """);
    testVisitor(text, "main", """
        public static void main(String[] args) {
          code:  // [Replace[range=(102,141), substring=null, regex=code:, region=null, replacement=...]]
          System.out.println("Hello"); // [Highlight[range=(150,177), substring=Hello, regex=null, region=null, type=HIGHLIGHTED]]
          for(int idx=0; idx<10; idx++) { // [Highlight[range=(219,251), substring=idx, regex=null, region=, type=HIGHLIGHTED]]
            System.out.println(idx); // [Highlight[range=(219,251), substring=idx, regex=null, region=, type=HIGHLIGHTED]]
          } // [Highlight[range=(219,251), substring=idx, regex=null, region=, type=HIGHLIGHTED]]
        }
      """);
  }

  @Test
  public void visitorNested() {
    String text = """
      // @highlight region="r1" substring="xxx" type=bold
      r1 start xxx
      // @highlight region="r2" substring="yyy" type=italic
      r2 start yyy
      // @highlight substring="zzz" type=highlighted:
      one line with zzz highlighting
      r1+r2 xxx
      // @end region="r1"
      r2 continues yyy
      // @end region="r2"
      """;
    testParsing(text, """
      Highlight[range=(4,51), substring=xxx, regex=null, region=r1, type=BOLD]
      PlainText[range=(52,64), content=r1 start xxx]
      Highlight[range=(69,118), substring=yyy, regex=null, region=r2, type=ITALIC]
      PlainText[range=(119,131), content=r2 start yyy]
      Highlight[range=(136,179), substring=zzz, regex=null, region=null, type=HIGHLIGHTED]
      PlainText[range=(180,210), content=one line with zzz highlighting]
      PlainText[range=(211,220), content=r1+r2 xxx]
      EndRegion[range=(225,240), region=r1]
      PlainText[range=(241,257), content=r2 continues yyy]
      EndRegion[range=(262,277), region=r2]
      PlainText[range=(278,278), content=]""");
    testVisitor(text, null, """
      r1 start xxx // [Highlight[range=(4,51), substring=xxx, regex=null, region=r1, type=BOLD]]
      r2 start yyy // [Highlight[range=(4,51), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,118), substring=yyy, regex=null, region=r2, type=ITALIC]]
      one line with zzz highlighting // [Highlight[range=(4,51), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,118), substring=yyy, regex=null, region=r2, type=ITALIC], Highlight[range=(136,179), substring=zzz, regex=null, region=null, type=HIGHLIGHTED]]
      r1+r2 xxx // [Highlight[range=(4,51), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,118), substring=yyy, regex=null, region=r2, type=ITALIC]]
      r2 continues yyy // [Highlight[range=(69,118), substring=yyy, regex=null, region=r2, type=ITALIC]]

      """);
    testVisitor(text, "r1", """
      r1 start xxx // [Highlight[range=(4,51), substring=xxx, regex=null, region=r1, type=BOLD]]
      r2 start yyy // [Highlight[range=(4,51), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,118), substring=yyy, regex=null, region=r2, type=ITALIC]]
      one line with zzz highlighting // [Highlight[range=(4,51), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,118), substring=yyy, regex=null, region=r2, type=ITALIC], Highlight[range=(136,179), substring=zzz, regex=null, region=null, type=HIGHLIGHTED]]
      r1+r2 xxx // [Highlight[range=(4,51), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,118), substring=yyy, regex=null, region=r2, type=ITALIC]]
      """);
    testVisitor(text, "r2", """
      r2 start yyy // [Highlight[range=(4,51), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,118), substring=yyy, regex=null, region=r2, type=ITALIC]]
      one line with zzz highlighting // [Highlight[range=(4,51), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,118), substring=yyy, regex=null, region=r2, type=ITALIC], Highlight[range=(136,179), substring=zzz, regex=null, region=null, type=HIGHLIGHTED]]
      r1+r2 xxx // [Highlight[range=(4,51), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,118), substring=yyy, regex=null, region=r2, type=ITALIC]]
      r2 continues yyy // [Highlight[range=(69,118), substring=yyy, regex=null, region=r2, type=ITALIC]]
      """);
  }
  
  private static void testParsing(@NotNull String input, @NotNull String expected) {
    assertEquals(expected, SnippetMarkup.parse(input).toString());
  }

  private static void testVisitor(@NotNull String input, @Nullable String region, @NotNull String expected) {
    var visitor = new SnippetMarkup.SnippetVisitor() {
      StringBuilder sb = new StringBuilder();
      
      @Override
      public void visitPlainText(@NotNull PlainText plainText,
                                 @NotNull List<@NotNull LocationMarkupNode> activeNodes) {
        sb.append(plainText.content());
        if (!activeNodes.isEmpty()) {
          sb.append(" // ").append(activeNodes);
        }
        sb.append("\n");
      }

      @Override
      public void visitError(@NotNull ErrorMarkup errorMarkup) {
        sb.append("Error: ").append(errorMarkup.message()).append("\n");
      }
    };
    
    SnippetMarkup.parse(input).visitSnippet(region, visitor);
    assertEquals(expected, visitor.sb.toString());
  }
}
