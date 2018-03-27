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
package org.intellij.lang.regexp;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.psi.IdentitySmartPointer;
import com.intellij.psi.PsiComment;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.testFramework.ParsingTestCase;

import java.io.IOException;
import java.util.EnumSet;

import static org.intellij.lang.regexp.RegExpCapability.POSIX_BRACKET_EXPRESSIONS;

/**
 * @author Bas Leijdekkers
 */
public class RegExpParsingTest extends ParsingTestCase {

  public RegExpParsingTest() {
    super("psi", "regexp", new RegExpParserDefinition());
  }

  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/RegExpSupport/testData";
  }

  public void testSimple1() throws IOException { doCodeTest("|"); }
  public void testSimple2() throws IOException { doCodeTest("(|\\$.*)\\.class"); }
  public void testSimple3() throws IOException { doCodeTest("abc"); }
  public void testSimple4() throws IOException { doCodeTest("multiple words of text"); }
  public void testSimple5() throws IOException { doCodeTest("ab|cd"); }
  public void testSimple6() throws IOException { doCodeTest("a*"); }
  public void testSimple7() throws IOException { doCodeTest("ab*c"); }
  public void testSimple8() throws IOException { doCodeTest("ab*bc"); }
  public void testSimple9() throws IOException { doCodeTest("ab+bc"); }
  public void testSimple10() throws IOException { doCodeTest("ab?bc"); }
  public void testSimple11() throws IOException { doCodeTest("ab?c"); }
  public void testSimple12() throws IOException { doCodeTest("a.c"); }
  public void testSimple13() throws IOException { doCodeTest("a.*c"); }
  public void testSimple14() throws IOException { doCodeTest("*a"); }
  public void testSimple15() throws IOException { doCodeTest("a{1}"); }
  public void testSimple16() throws IOException { doCodeTest("a{}"); }
  public void testSimple17() throws IOException { doCodeTest("a{"); }
  public void testSimple18() throws IOException { doCodeTest("a}"); }
  public void testSimple19() throws IOException { doCodeTest("a{1,}"); }
  public void testSimple20() throws IOException { doCodeTest("a{1,2}"); }
  public void testSimple21() throws IOException { doCodeTest("a{1,foo}"); }
  public void testSimple22() throws IOException { doCodeTest("\\;"); }

  public void testQuantifiers1() throws IOException { doCodeTest("a?"); }
  public void testQuantifiers2() throws IOException { doCodeTest("a+"); }
  public void testQuantifiers3() throws IOException { doCodeTest("a*"); }
  public void testQuantifiers4() throws IOException { doCodeTest("a??"); }
  public void testQuantifiers5() throws IOException { doCodeTest("a+?"); }
  public void testQuantifiers6() throws IOException { doCodeTest("a*?"); }
  public void testQuantifiers7() throws IOException { doCodeTest("a?+"); }
  public void testQuantifiers8() throws IOException { doCodeTest("a++"); }
  public void testQuantifiers9() throws IOException { doCodeTest("a*+"); }
  public void testQuantifiers10() throws IOException { doCodeTest("a**"); }
  public void testQuantifiers11() throws IOException { doCodeTest("a{2}"); }
  public void testQuantifiers12() throws IOException { doCodeTest("a{1,2}"); }
  public void testQuantifiers13() throws IOException { doCodeTest("a{2,1}"); }
  public void testQuantifiers14() throws IOException { doCodeTest("a{0,1}"); }
  public void testQuantifiers15() throws IOException { doCodeTest("a{1,}"); }
  public void testQuantifiers16() throws IOException { doCodeTest("a{0,}"); }
  public void testQuantifiers17() throws IOException { doCodeTest("a{1}"); }
  public void testQuantifiers18() throws IOException { doCodeTest("a{3,3}"); }
  public void testQuantifiers19() throws IOException { doCodeTest("a{"); }
  public void testQuantifiers20() throws IOException { doCodeTest("a}"); }
  public void testQuantifiers21() throws IOException { doCodeTest("a{}"); }

  public void testCharclasses1() throws IOException { doCodeTest("a[bc]d"); }
  public void testCharclasses2() throws IOException { doCodeTest("a[b-d]e"); }
  public void testCharclasses3() throws IOException { doCodeTest("a[b-d]"); }
  public void testCharclasses4() throws IOException { doCodeTest("a[b-a]"); }
  public void testCharclasses5() throws IOException { doCodeTest("a[-b]"); }
  public void testCharclasses6() throws IOException { doCodeTest("a[b-]"); }
  public void testCharclasses7() throws IOException { doCodeTest("[a-[b]]"); }
  public void testCharclasses8() throws IOException { doCodeTest("a[b&&[cd]]"); }
  public void testCharclasses9() throws IOException { doCodeTest("a[b-&&[cd]]"); }
  public void testCharclasses10() throws IOException { doCodeTest("a[b&&-]"); }
  public void testCharclasses11() throws IOException { doCodeTest("a[b&&-b]"); }
  public void testCharclasses12() throws IOException { doCodeTest("[&&]"); }
  public void testCharclasses13() throws IOException { doCodeTest("[&&[^\\d]]"); }
  public void testCharclasses14() throws IOException { doCodeTest("[a&&]"); }
  public void testCharclasses15() throws IOException { doCodeTest("a[b&&c&&d]"); }
  public void testCharclasses16() throws IOException { doCodeTest("a[b&&c&&d-e&&f]"); }
  public void testCharclasses17() throws IOException { doCodeTest("[a&&]"); }
  public void testCharclasses18() throws IOException { doCodeTest("a[a[b][c]]"); }
  public void testCharclasses19() throws IOException { doCodeTest("[a-[]]"); }
  public void testCharclasses20() throws IOException { doCodeTest("[a-[b"); }
  public void testCharclasses21() throws IOException { doCodeTest("[a[^b]]"); }
  public void testCharclasses22() throws IOException { doCodeTest("a[a[b[c]][d]]"); }
  public void testCharclasses23() throws IOException { doCodeTest("a[\\t--]"); }
  public void testCharclasses24() throws IOException { doCodeTest("a[\\t--]"); }
  public void testCharclasses25() throws IOException { doCodeTest("a[\\t---]"); }
  public void testCharclasses26() throws IOException { doCodeTest("a[-]?c"); }
  public void testCharclasses27() throws IOException { doCodeTest("a["); }
  public void testCharclasses28() throws IOException { doCodeTest("a]"); }
  public void testCharclasses29() throws IOException { doCodeTest("[a-["); }
  public void testCharclasses30() throws IOException { doCodeTest("a[]]"); }
  public void testCharclasses31() throws IOException { doCodeTest("a[^bc]d"); }
  public void testCharclasses32() throws IOException { doCodeTest("a[^bc]"); }
  public void testCharclasses33() throws IOException { doCodeTest("a[]b"); }
  public void testCharclasses34() throws IOException { doCodeTest("[^]"); }
  public void testCharclasses35() throws IOException { doCodeTest("[abhgefdc]ij"); }
  public void testCharclasses36() throws IOException { doCodeTest("[a-zA-Z_][a-zA-Z0-9_]*"); }
  public void testCharclasses37() throws IOException { doCodeTest("([a-c]+?)c"); }
  public void testCharclasses38() throws IOException { doCodeTest("([ab]*?)b"); }
  public void testCharclasses39() throws IOException { doCodeTest("([ab]*)b"); }
  public void testCharclasses40() throws IOException { doCodeTest("([ab]??)b"); }
  public void testCharclasses41() throws IOException { doCodeTest("(c[ab]?)b"); }
  public void testCharclasses42() throws IOException { doCodeTest("(c[ab]??)b"); }
  public void testCharclasses43() throws IOException { doCodeTest("(c[ab]*?)b"); }
  public void testCharclasses44() throws IOException { doCodeTest("a[bcd]+dcdcde"); }
  public void testCharclasses45() throws IOException { doCodeTest("[k]"); }
  public void testCharclasses46() throws IOException { doCodeTest("a[bcd]*dcdcde"); }
  public void testCharclasses47() throws IOException { doCodeTest("[^ab]*"); }
  public void testCharclasses48() throws IOException { doCodeTest("a[.]b"); }
  public void testCharclasses49() throws IOException { doCodeTest("a[+*?]b"); }
  public void testCharclasses50() throws IOException { doCodeTest("a[\\p{IsDigit}\\p{IsAlpha}]b"); }
  public void testCharclasses51() throws IOException { doCodeTest("[\\p{L}&&[^\\p{Lu}]]"); }
  public void testCharclasses52() throws IOException { doCodeTest("\\pL\\pM\\pZ\\pS\\pN\\pP\\pC\\PL\\PM\\PZ\\PS\\PN\\PP\\PC"); }
  public void testCharclasses53() throws IOException { doCodeTest("\\pA"); }
  public void testCharclasses54() throws IOException { doCodeTest("\\pl"); }
  public void testCharclasses55() throws IOException { doCodeTest("a\\p"); }
  public void testCharclasses56() throws IOException { doCodeTest("a\\p{}"); }
  public void testCharclasses57() throws IOException { doCodeTest("a\\p}"); }
  public void testCharclasses58() throws IOException { doCodeTest("a\\p{123}"); }
  public void testCharclasses59() throws IOException { doCodeTest("[\\p{nothing}]"); }
  public void testCharclasses60() throws IOException { doCodeTest("a\\p{*}b"); }
  public void testCharclasses61() throws IOException { doCodeTest("[\\w-\\w]"); }
  public void testCharclasses62() throws IOException { doCodeTest("[a-\\w]"); }
  public void testCharclasses63() throws IOException { doCodeTest("(?x)abc #foo \\q bar\n# foo\n(?-xi)xyz(?i:ABC)"); }
  public void testCharclasses64() throws IOException { doCodeTest("[\\ud800\\udc00-\\udbff\\udfff]"); }
  public void testCharclasses65() throws IOException { doCodeTest("\\R"); }
  public void testCharclasses66() throws IOException { doCodeTest("\\X"); }
  public void testCharclasses67() throws IOException { doCodeTest("\\-[\\*\\-\\[\\]\\\\\\+]"); }
  public void testCharclasses68() throws IOException { doCodeTest("[\\b]"); }
  public void testCharClasses69() throws IOException { doCodeTest("\\p{^L}"); }
  public void testCharClasses70() throws IOException { doCodeTest("[&&&&a]"); }
  public void testCharClasses71() throws IOException { doCodeTest("[a-\\Qz\\E]"); }
  public void testCharClasses72() throws IOException { doCodeTest("([\\^])"); }

  public void testGroups1() throws IOException { doCodeTest("()ef"); }
  public void testGroups2() throws IOException { doCodeTest("()*"); }
  public void testGroups3() throws IOException { doCodeTest("()"); }
  public void testGroups4() throws IOException { doCodeTest("(|)"); }
  public void testGroups5() throws IOException { doCodeTest("(*)b"); }
  public void testGroups6() throws IOException { doCodeTest("((a))"); }
  public void testGroups7() throws IOException { doCodeTest("(a)b(c)"); }
  public void testGroups8() throws IOException { doCodeTest("(a*)*"); }
  public void testGroups9() throws IOException { doCodeTest("(a*)+"); }
  public void testGroups10() throws IOException { doCodeTest("(a|)*"); }
  public void testGroups11() throws IOException { doCodeTest("(ab|cd)e"); }
  public void testGroups12() throws IOException { doCodeTest("(.*)c(.*)"); }
  public void testGroups13() throws IOException { doCodeTest("\\((.*), (.*)\\)"); }
  public void testGroups14() throws IOException { doCodeTest("a(bc)d"); }
  public void testGroups15() throws IOException { doCodeTest("([abc])*d"); }
  public void testGroups16() throws IOException { doCodeTest("((((((((((a)))))))))"); }
  public void testGroups17() throws IOException { doCodeTest("([abc])*bcd"); }
  public void testGroups18() throws IOException { doCodeTest("(a|b)c*d"); }
  public void testGroups19() throws IOException { doCodeTest("a([bc]*)c*"); }
  public void testGroups20() throws IOException { doCodeTest("((a)(b)c)(d)"); }
  public void testGroups21() throws IOException { doCodeTest("(ab|a)b*c"); }
  public void testGroups22() throws IOException { doCodeTest("(ab|ab*)bc"); }
  public void testGroups23() throws IOException { doCodeTest("(a|b|c|d|e)f"); }
  public void testGroups24() throws IOException { doCodeTest("a([bc]*)(c*d)"); }
  public void testGroups25() throws IOException { doCodeTest("a([bc]+)(c*d)"); }
  public void testGroups26() throws IOException { doCodeTest("a([bc]*)(c+d)"); }
  public void testGroups27() throws IOException { doCodeTest("(a+|b)*"); }
  public void testGroups28() throws IOException { doCodeTest("(a+|b)+"); }
  public void testGroups29() throws IOException { doCodeTest("(a+|b)?"); }
  public void testGroups30() throws IOException { doCodeTest("(^*"); }
  public void testGroups31() throws IOException { doCodeTest(")("); }
  public void testGroups32() throws IOException { doCodeTest("(?i:*)"); }
  public void testGroups33() throws IOException { doCodeTest("(?<asdf>[a-c])\\1"); }
  public void testGroups34() throws IOException { doCodeTest("(?<asdf>[a-c])\\k<asdf>"); }
  public void testGroups35() throws IOException { doCodeTest("\\k<adsf>"); }
  public void testGroups36() throws IOException { doCodeTest("(?P<name>{"); }
  public void testGroups37() throws IOException { doCodeTest("(?P=name)"); }
  public void testGroups38() throws IOException { doCodeTest("\\g'name'"); }
  public void testGroups39() throws IOException { doCodeTest("(?(name)yes-pattern|no-pattern)"); }
  public void testGroups40() throws IOException { doCodeTest("(?(name)yes-pattern|{"); }
  public void testGroups41() throws IOException { doCodeTest("(?>atomic)"); }
  public void testGroups42() throws IOException { doCodeTest("(?:non-capturing)"); }
  public void testGroups43() throws IOException { doCodeTest("(?(name)yes-pattern|no_pattern|maybe-pattern)"); }

  public void testEscapes1() throws IOException { doCodeTest("\\q"); }
  public void testEscapes2() throws IOException { doCodeTest("\\#"); }
  public void testEscapes3() throws IOException { doCodeTest("a\\"); }
  public void testEscapes4() throws IOException { doCodeTest("a\\(b"); }
  public void testEscapes5() throws IOException { doCodeTest("a\\(*b"); }
  public void testEscapes6() throws IOException { doCodeTest("a\\\\b"); }
  public void testEscapes7() throws IOException { doCodeTest("\\u004a"); }
  public void testEscapes8() throws IOException { doCodeTest("\\0123"); }
  public void testEscapes9() throws IOException { doCodeTest("\\0"); }
  public void testEscapes10() throws IOException { doCodeTest("\\x4a"); }
  public void testEscapes11() throws IOException { doCodeTest("\\x{0}"); }
  public void testEscapes12() throws IOException { doCodeTest("\\x{2011F}"); }
  public void testEscapes13() throws IOException { doCodeTest("[\\x4a-\\x4b]"); }
  public void testEscapes14() throws IOException { doCodeTest("[a-a]"); }
  public void testEscapes15() throws IOException { doCodeTest("[\\x4a-\\x3f]"); }
  public void testEscapes16() throws IOException { doCodeTest("[\\udbff\\udfff-\\ud800\\udc00]"); }
  public void testEscapes17() throws IOException { doCodeTest("[\\ud800\\udc00-\\udbff\\udfff]"); }
  public void testEscapes18() throws IOException { doCodeTest("[z-a]"); }
  public void testEscapes19() throws IOException { doCodeTest("[a-z]"); }
  public void testEscapes20() throws IOException { doCodeTest("a\\Qabc?*+.))]][]\\Eb"); }
  public void testEscapes21() throws IOException { doCodeTest("(a\\Qabc?*+.))]][]\\Eb)"); }
  public void testEscapes22() throws IOException { doCodeTest("[\\Qabc?*+.))]][]\\E]"); }
  public void testEscapes23() throws IOException { doCodeTest("a\\Qabc?*+.))]][]\\E)"); }
  public void testEscapes24() throws IOException { doCodeTest("\\Q\\j\\E"); }
  public void testEscapes25() throws IOException { doCodeTest("\\c0"); }
  public void testEscapes26() throws IOException { doCodeTest("[\\]]"); }
  public void testEscapes27() throws IOException { doCodeTest("[^\\]]"); }
  public void testEscapes28() throws IOException { doCodeTest("[a\\]]"); }
  public void testEscapes29() throws IOException { doCodeTest("[^a\\]]"); }

  public void testAnchors1() throws IOException { doCodeTest("^*"); }
  public void testAnchors2() throws IOException { doCodeTest("$*"); }
  public void testAnchors3() throws IOException { doCodeTest("^abc"); }
  public void testAnchors4() throws IOException { doCodeTest("^abc$"); }
  public void testAnchors5() throws IOException { doCodeTest("abc$"); }
  public void testAnchors6() throws IOException { doCodeTest("^"); }
  public void testAnchors7() throws IOException { doCodeTest("$"); }
  public void testAnchors8() throws IOException { doCodeTest("$b"); }
  public void testAnchors9() throws IOException { doCodeTest("^(ab|cd)e"); }
  public void testAnchors10() throws IOException { doCodeTest("^a(bc+|b[eh])g|.h$"); }

  public void testNamedchars1() throws IOException { doCodeTest("a*b\\s+c"); }
  public void testNamedchars2() throws IOException { doCodeTest("\\d+"); }
  public void testNamedchars3() throws IOException { doCodeTest("^\\p{javaJavaIdentifierStart}+\\p{javaJavaIdentifierPart}+$"); }
  public void testNamedchars4() throws IOException { doCodeTest("\\p{IsDigit}\\p{IsAlpha}"); }
  public void testNamedchars5() throws IOException { doCodeTest("\\p{InLATIN_1_SUPPLEMENT}"); }
  public void testNamedchars6() throws IOException { doCodeTest("[a-e]?d\\\\e"); }
  public void testNamedchars7() throws IOException { doCodeTest("((\\w+)/)*(\\w+)"); }
  public void testNamedchars8() throws IOException { doCodeTest("\\p{Digit}+"); }
  public void testNamedchars9() throws IOException { doCodeTest("[:xdigit:]+"); }
  public void testNamedchars10() throws IOException { doCodeTest("\\p{unknown}+"); }
  public void testNamedchars11() throws IOException { doCodeTest("[:^xdigit:]+"); }
  public void testNamedchars12() throws IOException { doCodeTest("\\p{InArabic Extended-A}"); }
  public void testNamedchars13() throws IOException { doCodeTest("\\N{Mahjong Tile Winter}"); }
  public void testNamedchars14() throws IOException { doCodeTest("[\\N{Mahjong Tile Winter}]"); }
  public void testNamedchars15() throws IOException { doCodeTest("[\\N{LATIN SMALL LETTER A}-\\N{LATIN SMALL LETTER Z}]"); }

  public void testBackrefs1() throws IOException { doCodeTest("(ac*)c*d[ac]*\\1"); }
  public void testBackrefs2() throws IOException { doCodeTest("(.)=\\1"); }
  public void testBackrefs3() throws IOException { doCodeTest("([ab])=\\1"); }
  public void testBackrefs4() throws IOException { doCodeTest("([ab]+)=\\1"); }
  public void testBackrefs5() throws IOException { doCodeTest("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)\\105"); }
  public void testBackrefs6() throws IOException { doCodeTest("(.)\\11"); }
  public void testBackrefs7() throws IOException { doCodeTest("([ab]+)=\\2"); }
  public void testBackrefs8() throws IOException { doCodeTest("([ab]+)=\\3"); }
  public void testBackrefs9() throws IOException { doCodeTest("([ab]+=\\1)"); }

  public void testComplex1() throws IOException { doCodeTest("z(\\w\\s+(?:\\w\\s+\\w)+)z"); }
  public void testComplex2() throws IOException { doCodeTest("(([hH][tT]{2}[pP]|[fF][tT][pP]):\\/\\/)?[a-zA-Z0-9\\-]+(\\.[a-zA-Z0-9\\-]+)*"); }
  public void testComplex3() throws IOException { doCodeTest("((?:[hH][tT]{2}[pP]|[fF][tT][pP]):\\/\\/)?[a-zA-Z0-9\\-]+(\\.[a-zA-Z0-9\\-]+)*"); }
  public void testComplex4() throws IOException { doCodeTest("(([hH][tT]{2}[pP]|[fF][tT][pP]):\\/\\/)?[a-zA-Z0-9\\-]+(?:\\.[a-zA-Z0-9\\-]+)*"); }
  public void testComplex5() throws IOException { doCodeTest("(?:([hH][tT]{2}[pP]|[fF][tT][pP]):\\/\\/)?[a-zA-Z0-9\\-]+(\\.[a-zA-Z0-9\\-]+)*"); }
  public void testComplex6() throws IOException { doCodeTest("^(?:([hH][tT]{2}[pP]|[fF][tT][pP]):\\/\\/)?[a-zA-Z0-9\\-]+(\\.[a-zA-Z0-9\\-]+)*$"); }
  public void testComplex7() throws IOException { doCodeTest("^(?:(?:[hH][tT]{2}[pP]|[fF][tT][pP]):\\/\\/)?[a-zA-Z0-9\\-]+(?:\\.[a-zA-Z0-9\\-]+)*$"); }

  public void testIncomplete1() throws IOException { doCodeTest("abc\\"); }
  public void testIncomplete2() throws IOException { doCodeTest("abc[\\"); }
  public void testIncomplete3() throws IOException { doCodeTest("abc\\x"); }
  public void testIncomplete4() throws IOException { doCodeTest("abc\\x1"); }
  public void testIncomplete5() throws IOException { doCodeTest("abc\\x{"); }
  public void testIncomplete6() throws IOException { doCodeTest("abc\\x{}"); }
  public void testIncomplete7() throws IOException { doCodeTest("abc\\x{0"); }
  public void testIncomplete8() throws IOException { doCodeTest("abc\\u"); }
  public void testIncomplete9() throws IOException { doCodeTest("abc\\u22"); }
  public void testIncomplete10() throws IOException { doCodeTest("\\Qabc"); }
  public void testIncomplete11() throws IOException { doCodeTest("\\Q"); }
  public void testIncomplete12() throws IOException { doCodeTest("\\E"); }
  public void testIncomplete13() throws IOException { doCodeTest("a|*"); }

  public void testRegressions1() throws IOException { doCodeTest("("); }
  public void testRegressions2() throws IOException { doCodeTest("[^^]"); }
  public void testRegressions3() throws IOException { doCodeTest("a)b"); }
  public void testRegressions4() throws IOException { doCodeTest("\\s*@return(?:s)?\\s*(?:(?:\\{|:)?\\s*(?([^\\s\\}]+)\\s*\\}?\\s*)?(.*)"); }

  public void testOptions1() throws IOException { doCodeTest("(?iZm)abc"); }
  public void testOptions2() throws IOException { doCodeTest("(?idmsuxU)nice"); }
  public void testOptions3() throws IOException { doCodeTest("(?idm-suxU)one(?suxU-idm)two"); }

  public void testTests1() throws IOException { doCodeTest("abc)"); }
  public void testTests2() throws IOException { doCodeTest("(abc"); }
  public void testTests3() throws IOException { doCodeTest("a+b+c"); }
  public void testTests4() throws IOException { doCodeTest("a**"); }
  public void testTests5() throws IOException { doCodeTest("a++"); }
  public void testTests6() throws IOException { doCodeTest("ab*"); }
  public void testTests7() throws IOException { doCodeTest("abcd*efg"); }
  public void testTests8() throws IOException { doCodeTest("a|b|c|d|e"); }
  public void testTests9() throws IOException { doCodeTest("(bc+d$|ef*g.|h?i(j|k))"); }
  public void testTests10() throws IOException { doCodeTest("a*(b*c*)"); }
  public void testTests11() throws IOException { doCodeTest("a?b+c*"); }
  public void testTests12() throws IOException { doCodeTest("i am a green (giant|man|martian)"); }
  public void testTests13() throws IOException { doCodeTest("(wee|week)(knights|knight)"); }
  public void testTests14() throws IOException { doCodeTest("(a.*b)(a.*b)"); }
  public void testTests15() throws IOException { doCodeTest("(\\s*\\w+)?"); }
  public void testTests16() throws IOException { doCodeTest("(?:a)"); }
  public void testTests17() throws IOException { doCodeTest("(?:\\w)"); }
  public void testTests18() throws IOException { doCodeTest("(?:\\w\\s\\w)+"); }
  public void testTests19() throws IOException { doCodeTest("(a\\w)(?:,(a\\w))+"); }
  public void testTests20() throws IOException { doCodeTest("abc.*?x+yz"); }
  public void testTests21() throws IOException { doCodeTest("abc.+?x+yz"); }
  public void testTests22() throws IOException { doCodeTest("a.+?(c|d)"); }
  public void testTests23() throws IOException { doCodeTest("a.+(c|d)"); }
  public void testTests24() throws IOException { doCodeTest("a+?b+?c+?"); }

  public void testRealLife1() throws IOException { doCodeTest("x:found=\"(true|false)\""); }
  public void testRealLife2() throws IOException { doCodeTest("(?:\\s)|(?:/\\*.*\\*/)|(?://[^\\n]*)"); }
  public void testRealLife3() throws IOException { doCodeTest("((?:\\p{Alpha}\\:)?[0-9 a-z_A-Z\\-\\\\./]+)"); }
  public void testRealLife4() throws IOException { doCodeTest("^[\\w\\+\\.\\-]{2,}:"); }
  public void testRealLife5() throws IOException { doCodeTest("#(.*)$"); }
  public void testRealLife6() throws IOException { doCodeTest("^(([^:]+)://)?([^:/]+)(:([0-9]+))?(/.*)"); }
  public void testRealLife7() throws IOException { doCodeTest("(([^:]+)://)?([^:/]+)(:([0-9]+))?(/.*)"); }
  public void testRealLife8() throws IOException { doCodeTest("usd [+-]?[0-9]+.[0-9][0-9]"); }
  public void testRealLife9() throws IOException { doCodeTest("\\b(\\w+)(\\s+\\1)+\\b"); }
  public void testRealLife10() throws IOException { doCodeTest(".*?(<(error|warning|info)(?: descr=\"((?:[^\"\\\\]|\\\\\")*)\")?(?: type=\"([0-9A-Z_]+)\")?(?: foreground=\"([0-9xa-f]+)\")?(?: background=\"([0-9xa-f]+)\")?(?: effectcolor=\"([0-9xa-f]+)\")?(?: effecttype=\"([A-Z]+)\")?(?: fonttype=\"([0-9]+)\")?(/)?>)(.*)"); }

  public void testBug1() throws IOException { doCodeTest("[{][\\w\\.]*[}]"); }
  public void testBug2() throws IOException { doCodeTest("[a-z0-9!\\#$%&'*+/=?^_`{|}~-]+"); }
  public void testBug3() throws IOException { doCodeTest("[\\{]"); }
  public void testBug4() throws IOException { doCodeTest("{"); }
  public void testBug5() throws IOException { doCodeTest("\\{"); }
  public void testBug6() throws IOException { doCodeTest("(<=\\s)-{3,}(?>\\s)"); }
  public void testBug7() throws IOException { doCodeTest("(?x)a\\ b\\ c"); }
  public void testBug8() throws IOException { doCodeTest("a\\ b"); }
  public void testBug9() throws IOException { doCodeTest("(^|\\.)\\*(?=(\\.|$))"); }
  public void testBug10() throws IOException { doCodeTest("\\h \\H \\v \\V"); }

  public void testParse1() throws IOException { doCodeTest("123 | 456"); }
  public void testParse2() throws IOException { doCodeTest("1**"); }
  public void testParse3() throws IOException { doCodeTest("(([hH][tT]{2}[pP]|[fF][tT][pP])://)?[a-zA-Z0-9\\-]+(\\.[a-zA-Z0-9\\-]+)*"); }

  public void testCategoryShorthand1() throws IOException { doCodeTest("\\pL"); }

  public void testCapabilitiesProvider() throws IOException {
    RegExpCapabilitiesProvider provider = (host, def) -> EnumSet.of(POSIX_BRACKET_EXPRESSIONS);
    try {
      RegExpCapabilitiesProvider.EP.addExplicitExtension(RegExpLanguage.INSTANCE, provider);
      PsiComment context = SyntaxTraverser.psiTraverser(createPsiFile("c", "(?#xxx)")).filter(PsiComment.class).first();
      myFile = createPsiFile("a", "[[:blank:]]");
      FileContextUtil.INJECTED_IN_ELEMENT.set(myFile, new IdentitySmartPointer<>(context));
      ensureParsed(myFile);
      checkResult(myFilePrefix + getTestName(), myFile);
    }
    finally {
      RegExpCapabilitiesProvider.EP.removeExplicitExtension(RegExpLanguage.INSTANCE, provider);
    }
  }
}
