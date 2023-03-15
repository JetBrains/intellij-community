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
        StartRegion[range=(22,40), region=test]
        PlainText[range=(41,57), content=  void main() {
        ]
        Replace[range=(93,133), selector=Substring[substring=Hello], region=null, replacement=...]
        PlainText[range=(57,90), content=    System.out.println("Hello");\s
        ]
        Highlight[range=(141,167), selector=Substring[substring=Hello], region=null, type=HIGHLIGHTED]
        PlainText[range=(170,203), content=    System.out.println("Hello");
        ]
        Highlight[range=(210,227), selector=WholeLine[], region=, type=HIGHLIGHTED]
        PlainText[range=(229,263), content=    System.out.println("Hello1");
        ]
        Link[range=(300,344), selector=Regex[pattern=H\\w+], region=null, target=Hello, linkType=LINKPLAIN]
        PlainText[range=(263,297), content=    System.out.println("Hello2");\s
        ]
        EndRegion[range=(352,356), region=null]
        PlainText[range=(357,361), content=  }
        ]
        EndRegion[range=(366,370), region=null]
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
            doubleReplace // @replace substring="Hello" regex="Hello" replacement="Hello"
            malformed // @replace regex="???" replacement="xyz"
          }
          // @end test
        }
        """, """
        PlainText[range=(0,17), content=public class X {
        ]
        ErrorMarkup[range=(22,28), message=@start: missing 'region' attribute]
        PlainText[range=(29,45), content=  void main() {
        ]
        ErrorMarkup[range=(108,128), message=@replace: unsupported attribute: 'substring2']
        ErrorMarkup[range=(81,128), message=@replace: missing 'replacement' attribute]
        PlainText[range=(45,78), content=    System.out.println("Hello");\s
        ]
        ErrorMarkup[range=(147,161), message=@highlight: unknown type 'underline'; only 'bold', 'italic', and 'highlighted' are supported]
        Highlight[range=(136,161), selector=WholeLine[], region=null, type=HIGHLIGHTED]
        PlainText[range=(129,129), content=]
        ErrorMarkup[range=(198,213), message=@link: missing 'target' attribute]
        ErrorMarkup[range=(204,213), message=@link: unknown type 'pfff'; only 'link' and 'linkplain' are supported]
        PlainText[range=(162,195), content=    System.out.println("Hello");\s
        ]
        ErrorMarkup[range=(262,275), message=@replace: either regex or substring should be specified but not both]
        PlainText[range=(214,232), content=    doubleReplace\s
        ]
        ErrorMarkup[range=(322,333), message=@replace: malformed regular expression: Dangling meta character '?' near index 0
        ???
        ^]
        PlainText[range=(296,310), content=    malformed\s
        ]
        PlainText[range=(352,356), content=  }
        ]
        ErrorMarkup[range=(366,370), message=@end: unsupported attribute: 'test']
        EndRegion[range=(361,370), region=null]
        PlainText[range=(371,373), content=}
        ]
        PlainText[range=(373,373), content=]""");
  }

  @Test
  public void malformed() {
    testParsing("""
                  Hello // @replace @pff
                  """, """
      ErrorMarkup[range=(9,17), message=@replace: missing 'replacement' attribute]
      ErrorMarkup[range=(18,22), message=Markup tag or attribute expected]
      PlainText[range=(0,6), content=Hello\s
      ]
      PlainText[range=(23,23), content=]""");
  }

  @Test
  public void lineNoColon() {
    String text = """
      public class Hello {
        //   @replace substring="xxx" replacement='xyz'
        System.out.println("xxx");
      }
      """;
    testParsing(text, """
      PlainText[range=(0,21), content=public class Hello {
      ]
      Replace[range=(28,70), selector=Substring[substring=xxx], region=null, replacement=xyz]
      PlainText[range=(21,21), content=]
      PlainText[range=(71,100), content=  System.out.println("xxx");
      ]
      PlainText[range=(100,102), content=}
      ]
      PlainText[range=(102,102), content=]""");
  }
  
  @Test
  public void visitorMultiTag() {
    String text = """
      public class Hello {
        // @replace substring="xyz" replacement='xxx' @link substring="System" target="java.lang.System" @highlight:
        System.out.println("xyz");
      }
      """;
    testParsing(text, """
      PlainText[range=(0,21), content=public class Hello {
      ]
      Replace[range=(26,68), selector=Substring[substring=xyz], region=null, replacement=xxx]
      Link[range=(69,119), selector=Substring[substring=System], region=null, target=java.lang.System, linkType=LINK]
      Highlight[range=(120,130), selector=WholeLine[], region=null, type=HIGHLIGHTED]
      PlainText[range=(132,161), content=  System.out.println("xyz");
      ]
      PlainText[range=(161,163), content=}
      ]
      PlainText[range=(163,163), content=]""");
    testVisitor(text, null, """
      public class Hello {
      // [Replace[range=(26,68), selector=Substring[substring=xyz], region=null, replacement=xxx], Link[range=(69,119), selector=Substring[substring=System], region=null, target=java.lang.System, linkType=LINK], Highlight[range=(120,130), selector=WholeLine[], region=null, type=HIGHLIGHTED]]
        System.out.println("xyz");
      }
      """);
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
      StartRegion[range=(26,44), region=main]
      PlainText[range=(45,88), content=  public static void main(String[] args) {
      ]
      Replace[range=(101,138), selector=Regex[pattern=code:], region=null, replacement=]
      PlainText[range=(88,98), content=    code:\s
      ]
      Highlight[range=(146,180), selector=Substring[substring=Hello World], region=null, type=HIGHLIGHTED]
      PlainText[range=(139,139), content=]
      PlainText[range=(181,220), content=    System.out.println("Hello World");
      ]
      Highlight[range=(227,260), selector=Substring[substring=idx], region=, type=HIGHLIGHTED]
      PlainText[range=(261,297), content=    for(int idx=0; idx<10; idx++) {
      ]
      PlainText[range=(297,328), content=      System.out.println(idx);
      ]
      PlainText[range=(328,334), content=    }
      ]
      EndRegion[range=(341,345), region=null]
      PlainText[range=(346,350), content=  }
      ]
      EndRegion[range=(355,359), region=null]
      PlainText[range=(360,362), content=}
      ]
      PlainText[range=(362,362), content=]""");
    testVisitor(text, null, """
      public class Hello {
        public static void main(String[] args) {
      // [Replace[range=(101,138), selector=Regex[pattern=code:], region=null, replacement=]]
          code:\s
      // [Highlight[range=(146,180), selector=Substring[substring=Hello World], region=null, type=HIGHLIGHTED]]
          System.out.println("Hello World");
      // [Highlight[range=(227,260), selector=Substring[substring=idx], region=, type=HIGHLIGHTED]]
          for(int idx=0; idx<10; idx++) {
      // [Highlight[range=(227,260), selector=Substring[substring=idx], region=, type=HIGHLIGHTED]]
            System.out.println(idx);
      // [Highlight[range=(227,260), selector=Substring[substring=idx], region=, type=HIGHLIGHTED]]
          }
        }
      }
      """);
    testVisitor(text, "main", """
        public static void main(String[] args) {
      // [Replace[range=(101,138), selector=Regex[pattern=code:], region=null, replacement=]]
          code:\s
      // [Highlight[range=(146,180), selector=Substring[substring=Hello World], region=null, type=HIGHLIGHTED]]
          System.out.println("Hello World");
      // [Highlight[range=(227,260), selector=Substring[substring=idx], region=, type=HIGHLIGHTED]]
          for(int idx=0; idx<10; idx++) {
      // [Highlight[range=(227,260), selector=Substring[substring=idx], region=, type=HIGHLIGHTED]]
            System.out.println(idx);
      // [Highlight[range=(227,260), selector=Substring[substring=idx], region=, type=HIGHLIGHTED]]
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
      Highlight[range=(3,51), selector=Substring[substring=xxx], region=r1, type=BOLD]
      PlainText[range=(52,65), content=r1 start xxx
      ]
      Highlight[range=(68,118), selector=Substring[substring=yyy], region=r2, type=ITALIC]
      PlainText[range=(119,132), content=r2 start yyy
      ]
      Highlight[range=(135,178), selector=Substring[substring=zzz], region=null, type=HIGHLIGHTED]
      PlainText[range=(180,211), content=one line with zzz highlighting
      ]
      PlainText[range=(211,221), content=r1+r2 xxx
      ]
      EndRegion[range=(224,240), region=r1]
      PlainText[range=(241,258), content=r2 continues yyy
      ]
      EndRegion[range=(261,277), region=r2]
      PlainText[range=(278,278), content=]""");
    testVisitor(text, null, """
      // [Highlight[range=(3,51), selector=Substring[substring=xxx], region=r1, type=BOLD]]
      r1 start xxx
      // [Highlight[range=(3,51), selector=Substring[substring=xxx], region=r1, type=BOLD], Highlight[range=(68,118), selector=Substring[substring=yyy], region=r2, type=ITALIC]]
      r2 start yyy
      // [Highlight[range=(3,51), selector=Substring[substring=xxx], region=r1, type=BOLD], Highlight[range=(68,118), selector=Substring[substring=yyy], region=r2, type=ITALIC], Highlight[range=(135,178), selector=Substring[substring=zzz], region=null, type=HIGHLIGHTED]]
      one line with zzz highlighting
      // [Highlight[range=(3,51), selector=Substring[substring=xxx], region=r1, type=BOLD], Highlight[range=(68,118), selector=Substring[substring=yyy], region=r2, type=ITALIC]]
      r1+r2 xxx
      // [Highlight[range=(68,118), selector=Substring[substring=yyy], region=r2, type=ITALIC]]
      r2 continues yyy
      """);
    testVisitor(text, "r1", """
      // [Highlight[range=(3,51), selector=Substring[substring=xxx], region=r1, type=BOLD]]
      r1 start xxx
      // [Highlight[range=(3,51), selector=Substring[substring=xxx], region=r1, type=BOLD], Highlight[range=(68,118), selector=Substring[substring=yyy], region=r2, type=ITALIC]]
      r2 start yyy
      // [Highlight[range=(3,51), selector=Substring[substring=xxx], region=r1, type=BOLD], Highlight[range=(68,118), selector=Substring[substring=yyy], region=r2, type=ITALIC], Highlight[range=(135,178), selector=Substring[substring=zzz], region=null, type=HIGHLIGHTED]]
      one line with zzz highlighting
      // [Highlight[range=(3,51), selector=Substring[substring=xxx], region=r1, type=BOLD], Highlight[range=(68,118), selector=Substring[substring=yyy], region=r2, type=ITALIC]]
      r1+r2 xxx
      """);
    testVisitor(text, "r2", """
      // [Highlight[range=(3,51), selector=Substring[substring=xxx], region=r1, type=BOLD], Highlight[range=(68,118), selector=Substring[substring=yyy], region=r2, type=ITALIC]]
      r2 start yyy
      // [Highlight[range=(3,51), selector=Substring[substring=xxx], region=r1, type=BOLD], Highlight[range=(68,118), selector=Substring[substring=yyy], region=r2, type=ITALIC], Highlight[range=(135,178), selector=Substring[substring=zzz], region=null, type=HIGHLIGHTED]]
      one line with zzz highlighting
      // [Highlight[range=(3,51), selector=Substring[substring=xxx], region=r1, type=BOLD], Highlight[range=(68,118), selector=Substring[substring=yyy], region=r2, type=ITALIC]]
      r1+r2 xxx
      // [Highlight[range=(68,118), selector=Substring[substring=yyy], region=r2, type=ITALIC]]
      r2 continues yyy
      """);
  }
  
  private static void testParsing(@NotNull String input, @NotNull String expected) {
    assertEquals(expected, SnippetMarkup.parse(input).toString());
  }

  private static void testVisitor(@NotNull String input, @Nullable String region, @NotNull String expected) {
    var visitor = new SnippetMarkup.SnippetVisitor() {
      final StringBuilder sb = new StringBuilder();
      
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
