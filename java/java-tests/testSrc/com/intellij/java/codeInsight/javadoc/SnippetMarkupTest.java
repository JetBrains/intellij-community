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
        PlainText[range=(0,9), content=Hello!!!
        ]
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
        PlainText[range=(0,17), content=public class X {
        ]
        StartRegion[range=(23,41), region=test]
        PlainText[range=(41,57), content=  void main() {
        ]
        Replace[range=(94,134), substring=Hello, regex=null, region=null, replacement=...]
        PlainText[range=(57,90), content=    System.out.println("Hello");\s
        ]
        Highlight[range=(142,170), substring=Hello, regex=null, region=null, type=HIGHLIGHTED]
        PlainText[range=(170,203), content=    System.out.println("Hello");
        ]
        Highlight[range=(211,229), substring=null, regex=null, region=, type=HIGHLIGHTED]
        PlainText[range=(229,263), content=    System.out.println("Hello1");
        ]
        Link[range=(301,345), substring=null, regex=H\\w+, region=null, target=Hello, linkType=LINKPLAIN]
        PlainText[range=(263,297), content=    System.out.println("Hello2");\s
        ]
        EndRegion[range=(353,357), region=null]
        PlainText[range=(357,361), content=  }
        ]
        EndRegion[range=(367,371), region=null]
        PlainText[range=(371,373), content=}
        ]
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
        PlainText[range=(0,17), content=public class X {
        ]
        ErrorMarkup[range=(23,29), message=@start: missing 'region' attribute]
        PlainText[range=(29,45), content=  void main() {
        ]
        Replace[range=(82,129), substring=Hello, regex=null, region=null, replacement=]
        ErrorMarkup[range=(108,128), message=Unsupported attribute: 'substring2']
        ErrorMarkup[range=(82,129), message=@replace: missing 'replacement' attribute]
        PlainText[range=(45,78), content=    System.out.println("Hello");\s
        ]
        Highlight[range=(137,162), substring=null, regex=null, region=null, type=HIGHLIGHTED]
        ErrorMarkup[range=(147,161), message=Unknown type 'underline'; only 'bold', 'italic', and 'highlighted' are supported]
        Link[range=(199,214), substring=null, regex=null, region=null, target=, linkType=LINK]
        ErrorMarkup[range=(199,214), message=@link: missing 'target' attribute]
        ErrorMarkup[range=(204,213), message=Unknown type 'pfff'; only 'link' and 'linkplain' are supported]
        PlainText[range=(162,195), content=    System.out.println("Hello");\s
        ]
        PlainText[range=(214,218), content=  }
        ]
        EndRegion[range=(224,233), region=null]
        ErrorMarkup[range=(228,232), message=Unsupported attribute: 'test']
        PlainText[range=(233,235), content=}
        ]
        PlainText[range=(235,235), content=]""");
  }
  
  @Test
  public void visitorSimple() {
    String text = """
      public class Hello {
        // @start region=main
        public static void main(String[] args) {
          code: // @replace regex='code:' replacement=""
          // @highlight substring="Hello World"
          System.out.println("Hello World");
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
      PlainText[range=(0,21), content=public class Hello {
      ]
      StartRegion[range=(27,45), region=main]
      PlainText[range=(45,88), content=  public static void main(String[] args) {
      ]
      Replace[range=(102,139), substring=null, regex=code:, region=null, replacement=]
      PlainText[range=(88,98), content=    code:\s
      ]
      Highlight[range=(147,181), substring=Hello World, regex=null, region=null, type=HIGHLIGHTED]
      PlainText[range=(181,220), content=    System.out.println("Hello World");
      ]
      Highlight[range=(228,261), substring=idx, regex=null, region=, type=HIGHLIGHTED]
      PlainText[range=(261,297), content=    for(int idx=0; idx<10; idx++) {
      ]
      PlainText[range=(297,328), content=      System.out.println(idx);
      ]
      PlainText[range=(328,334), content=    }
      ]
      EndRegion[range=(342,346), region=null]
      PlainText[range=(346,350), content=  }
      ]
      EndRegion[range=(356,360), region=null]
      PlainText[range=(360,362), content=}
      ]
      PlainText[range=(362,362), content=]""");
    testVisitor(text, null, """
      public class Hello {
        public static void main(String[] args) {
      // [Replace[range=(102,139), substring=null, regex=code:, region=null, replacement=]]
          code:\s
      // [Highlight[range=(147,181), substring=Hello World, regex=null, region=null, type=HIGHLIGHTED]]
          System.out.println("Hello World");
      // [Highlight[range=(228,261), substring=idx, regex=null, region=, type=HIGHLIGHTED]]
          for(int idx=0; idx<10; idx++) {
      // [Highlight[range=(228,261), substring=idx, regex=null, region=, type=HIGHLIGHTED]]
            System.out.println(idx);
      // [Highlight[range=(228,261), substring=idx, regex=null, region=, type=HIGHLIGHTED]]
          }
        }
      }
      """);
    testVisitor(text, "main", """
        public static void main(String[] args) {
      // [Replace[range=(102,139), substring=null, regex=code:, region=null, replacement=]]
          code:\s
      // [Highlight[range=(147,181), substring=Hello World, regex=null, region=null, type=HIGHLIGHTED]]
          System.out.println("Hello World");
      // [Highlight[range=(228,261), substring=idx, regex=null, region=, type=HIGHLIGHTED]]
          for(int idx=0; idx<10; idx++) {
      // [Highlight[range=(228,261), substring=idx, regex=null, region=, type=HIGHLIGHTED]]
            System.out.println(idx);
      // [Highlight[range=(228,261), substring=idx, regex=null, region=, type=HIGHLIGHTED]]
          }
        }
      """);
    SnippetMarkup markup = SnippetMarkup.parse(text);
    assertEquals(0, markup.getCommonIndent(null));
    assertEquals(2, markup.getCommonIndent("main"));
    assertEquals("""
                     public static void main(String[] args) {
                       code:\s
                       System.out.println("Hello World");
                       for(int idx=0; idx<10; idx++) {
                         System.out.println(idx);
                       }
                     }
                   """, markup.getTextWithoutMarkup("main"));
    
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
      Highlight[range=(4,52), substring=xxx, regex=null, region=r1, type=BOLD]
      PlainText[range=(52,65), content=r1 start xxx
      ]
      Highlight[range=(69,119), substring=yyy, regex=null, region=r2, type=ITALIC]
      PlainText[range=(119,132), content=r2 start yyy
      ]
      Highlight[range=(136,180), substring=zzz, regex=null, region=null, type=HIGHLIGHTED]
      PlainText[range=(180,211), content=one line with zzz highlighting
      ]
      PlainText[range=(211,221), content=r1+r2 xxx
      ]
      EndRegion[range=(225,241), region=r1]
      PlainText[range=(241,258), content=r2 continues yyy
      ]
      EndRegion[range=(262,278), region=r2]
      PlainText[range=(278,278), content=]""");
    testVisitor(text, null, """
      // [Highlight[range=(4,52), substring=xxx, regex=null, region=r1, type=BOLD]]
      r1 start xxx
      // [Highlight[range=(4,52), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,119), substring=yyy, regex=null, region=r2, type=ITALIC]]
      r2 start yyy
      // [Highlight[range=(4,52), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,119), substring=yyy, regex=null, region=r2, type=ITALIC], Highlight[range=(136,180), substring=zzz, regex=null, region=null, type=HIGHLIGHTED]]
      one line with zzz highlighting
      // [Highlight[range=(4,52), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,119), substring=yyy, regex=null, region=r2, type=ITALIC]]
      r1+r2 xxx
      // [Highlight[range=(69,119), substring=yyy, regex=null, region=r2, type=ITALIC]]
      r2 continues yyy
      """);
    testVisitor(text, "r1", """
      // [Highlight[range=(4,52), substring=xxx, regex=null, region=r1, type=BOLD]]
      r1 start xxx
      // [Highlight[range=(4,52), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,119), substring=yyy, regex=null, region=r2, type=ITALIC]]
      r2 start yyy
      // [Highlight[range=(4,52), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,119), substring=yyy, regex=null, region=r2, type=ITALIC], Highlight[range=(136,180), substring=zzz, regex=null, region=null, type=HIGHLIGHTED]]
      one line with zzz highlighting
      // [Highlight[range=(4,52), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,119), substring=yyy, regex=null, region=r2, type=ITALIC]]
      r1+r2 xxx
      """);
    testVisitor(text, "r2", """
      // [Highlight[range=(4,52), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,119), substring=yyy, regex=null, region=r2, type=ITALIC]]
      r2 start yyy
      // [Highlight[range=(4,52), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,119), substring=yyy, regex=null, region=r2, type=ITALIC], Highlight[range=(136,180), substring=zzz, regex=null, region=null, type=HIGHLIGHTED]]
      one line with zzz highlighting
      // [Highlight[range=(4,52), substring=xxx, regex=null, region=r1, type=BOLD], Highlight[range=(69,119), substring=yyy, regex=null, region=r2, type=ITALIC]]
      r1+r2 xxx
      // [Highlight[range=(69,119), substring=yyy, regex=null, region=r2, type=ITALIC]]
      r2 continues yyy
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
        if (!activeNodes.isEmpty()) {
          sb.append("// ").append(activeNodes).append("\n");
        }
        sb.append(plainText.content());
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
