// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.testFramework.ParsingTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static org.intellij.lang.regexp.RegExpCapability.POSIX_BRACKET_EXPRESSIONS;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class RegExpParsingTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  private final TestFixture<ParsingTestFixture> parsingFixture =
    parsingFixture(projectFixture, PathManagerEx.getCommunityHomePath() + "/RegExpSupport/testData/psi", "regexp",
                   new RegExpParserDefinition());

  @Test public void testSimple1() { parsingFixture.get().doCodeTest("|"); }
  @Test public void testSimple2() { parsingFixture.get().doCodeTest("(|\\$.*)\\.class"); }
  @Test public void testSimple3() { parsingFixture.get().doCodeTest("abc"); }
  @Test public void testSimple4() { parsingFixture.get().doCodeTest("multiple words of text"); }
  @Test public void testSimple5() { parsingFixture.get().doCodeTest("ab|cd"); }
  @Test public void testSimple6() { parsingFixture.get().doCodeTest("a*"); }
  @Test public void testSimple7() { parsingFixture.get().doCodeTest("ab*c"); }
  @Test public void testSimple8() { parsingFixture.get().doCodeTest("ab*bc"); }
  @Test public void testSimple9() { parsingFixture.get().doCodeTest("ab+bc"); }
  @Test public void testSimple10() { parsingFixture.get().doCodeTest("ab?bc"); }
  @Test public void testSimple11() { parsingFixture.get().doCodeTest("ab?c"); }
  @Test public void testSimple12() { parsingFixture.get().doCodeTest("a.c"); }
  @Test public void testSimple13() { parsingFixture.get().doCodeTest("a.*c"); }
  @Test public void testSimple14() { parsingFixture.get().doCodeTest("*a"); }
  @Test public void testSimple15() { parsingFixture.get().doCodeTest("a{1}"); }
  @Test public void testSimple16() { parsingFixture.get().doCodeTest("a{}"); }
  @Test public void testSimple17() { parsingFixture.get().doCodeTest("a{"); }
  @Test public void testSimple18() { parsingFixture.get().doCodeTest("a}"); }
  @Test public void testSimple19() { parsingFixture.get().doCodeTest("a{1,}"); }
  @Test public void testSimple20() { parsingFixture.get().doCodeTest("a{1,2}"); }
  @Test public void testSimple21() { parsingFixture.get().doCodeTest("a{1,foo}"); }
  @Test public void testSimple22() { parsingFixture.get().doCodeTest("\\;"); }
  @Test public void testSimple23() { parsingFixture.get().doCodeTest(""); }

  @Test public void testQuantifiers1() { parsingFixture.get().doCodeTest("a?"); }
  @Test public void testQuantifiers2() { parsingFixture.get().doCodeTest("a+"); }
  @Test public void testQuantifiers3() { parsingFixture.get().doCodeTest("a*"); }
  @Test public void testQuantifiers4() { parsingFixture.get().doCodeTest("a??"); }
  @Test public void testQuantifiers5() { parsingFixture.get().doCodeTest("a+?"); }
  @Test public void testQuantifiers6() { parsingFixture.get().doCodeTest("a*?"); }
  @Test public void testQuantifiers7() { parsingFixture.get().doCodeTest("a?+"); }
  @Test public void testQuantifiers8() { parsingFixture.get().doCodeTest("a++"); }
  @Test public void testQuantifiers9() { parsingFixture.get().doCodeTest("a*+"); }
  @Test public void testQuantifiers10() { parsingFixture.get().doCodeTest("a**"); }
  @Test public void testQuantifiers11() { parsingFixture.get().doCodeTest("a{2}"); }
  @Test public void testQuantifiers12() { parsingFixture.get().doCodeTest("a{1,2}"); }
  @Test public void testQuantifiers13() { parsingFixture.get().doCodeTest("a{2,1}"); }
  @Test public void testQuantifiers14() { parsingFixture.get().doCodeTest("a{0,1}"); }
  @Test public void testQuantifiers15() { parsingFixture.get().doCodeTest("a{1,}"); }
  @Test public void testQuantifiers16() { parsingFixture.get().doCodeTest("a{0,}"); }
  @Test public void testQuantifiers17() { parsingFixture.get().doCodeTest("a{1}"); }
  @Test public void testQuantifiers18() { parsingFixture.get().doCodeTest("a{3,3}"); }
  @Test public void testQuantifiers19() { parsingFixture.get().doCodeTest("a{"); }
  @Test public void testQuantifiers20() { parsingFixture.get().doCodeTest("a}"); }
  @Test public void testQuantifiers21() { parsingFixture.get().doCodeTest("a{}"); }
  @Test public void testQuantifiers22() { parsingFixture.get().doCodeTest("{1,2}"); }

  @Test public void testCharclasses1() { parsingFixture.get().doCodeTest("a[bc]d"); }
  @Test public void testCharclasses2() { parsingFixture.get().doCodeTest("a[b-d]e"); }
  @Test public void testCharclasses3() { parsingFixture.get().doCodeTest("a[b-d]"); }
  @Test public void testCharclasses4() { parsingFixture.get().doCodeTest("a[b-a]"); }
  @Test public void testCharclasses5() { parsingFixture.get().doCodeTest("a[-b]"); }
  @Test public void testCharclasses6() { parsingFixture.get().doCodeTest("a[b-]"); }
  @Test public void testCharclasses7() { parsingFixture.get().doCodeTest("[a-[b]]"); }
  @Test public void testCharclasses8() { parsingFixture.get().doCodeTest("a[b&&[cd]]"); }
  @Test public void testCharclasses9() { parsingFixture.get().doCodeTest("a[b-&&[cd]]"); }
  @Test public void testCharclasses10() { parsingFixture.get().doCodeTest("a[b&&-]"); }
  @Test public void testCharclasses11() { parsingFixture.get().doCodeTest("a[b&&-b]"); }
  @Test public void testCharclasses12() { parsingFixture.get().doCodeTest("[&&]"); }
  @Test public void testCharclasses13() { parsingFixture.get().doCodeTest("[&&[^\\d]]"); }
  @Test public void testCharclasses14() { parsingFixture.get().doCodeTest("[a&&]"); }
  @Test public void testCharclasses15() { parsingFixture.get().doCodeTest("a[b&&c&&d]"); }
  @Test public void testCharclasses16() { parsingFixture.get().doCodeTest("a[b&&c&&d-e&&f]"); }
  @Test public void testCharclasses17() { parsingFixture.get().doCodeTest("[a&&]"); }
  @Test public void testCharclasses18() { parsingFixture.get().doCodeTest("a[a[b][c]]"); }
  @Test public void testCharclasses19() { parsingFixture.get().doCodeTest("[a-[]]"); }
  @Test public void testCharclasses20() { parsingFixture.get().doCodeTest("[a-[b"); }
  @Test public void testCharclasses21() { parsingFixture.get().doCodeTest("[a[^b]]"); }
  @Test public void testCharclasses22() { parsingFixture.get().doCodeTest("a[a[b[c]][d]]"); }
  @Test public void testCharclasses23() { parsingFixture.get().doCodeTest("a[\\t--]"); }
  @Test public void testCharclasses24() { parsingFixture.get().doCodeTest("a[\\t--]"); }
  @Test public void testCharclasses25() { parsingFixture.get().doCodeTest("a[\\t---]"); }
  @Test public void testCharclasses26() { parsingFixture.get().doCodeTest("a[-]?c"); }
  @Test public void testCharclasses27() { parsingFixture.get().doCodeTest("a["); }
  @Test public void testCharclasses28() { parsingFixture.get().doCodeTest("a]"); }
  @Test public void testCharclasses29() { parsingFixture.get().doCodeTest("[a-["); }
  @Test public void testCharclasses30() { parsingFixture.get().doCodeTest("a[]]"); }
  @Test public void testCharclasses31() { parsingFixture.get().doCodeTest("a[^bc]d"); }
  @Test public void testCharclasses32() { parsingFixture.get().doCodeTest("a[^bc]"); }
  @Test public void testCharclasses33() { parsingFixture.get().doCodeTest("a[]b"); }
  @Test public void testCharclasses34() { parsingFixture.get().doCodeTest("[^]"); }
  @Test public void testCharclasses35() { parsingFixture.get().doCodeTest("[abhgefdc]ij"); }
  @Test public void testCharclasses36() { parsingFixture.get().doCodeTest("[a-zA-Z_][a-zA-Z0-9_]*"); }
  @Test public void testCharclasses37() { parsingFixture.get().doCodeTest("([a-c]+?)c"); }
  @Test public void testCharclasses38() { parsingFixture.get().doCodeTest("([ab]*?)b"); }
  @Test public void testCharclasses39() { parsingFixture.get().doCodeTest("([ab]*)b"); }
  @Test public void testCharclasses40() { parsingFixture.get().doCodeTest("([ab]??)b"); }
  @Test public void testCharclasses41() { parsingFixture.get().doCodeTest("(c[ab]?)b"); }
  @Test public void testCharclasses42() { parsingFixture.get().doCodeTest("(c[ab]??)b"); }
  @Test public void testCharclasses43() { parsingFixture.get().doCodeTest("(c[ab]*?)b"); }
  @Test public void testCharclasses44() { parsingFixture.get().doCodeTest("a[bcd]+dcdcde"); }
  @Test public void testCharclasses45() { parsingFixture.get().doCodeTest("[k]"); }
  @Test public void testCharclasses46() { parsingFixture.get().doCodeTest("a[bcd]*dcdcde"); }
  @Test public void testCharclasses47() { parsingFixture.get().doCodeTest("[^ab]*"); }
  @Test public void testCharclasses48() { parsingFixture.get().doCodeTest("a[.]b"); }
  @Test public void testCharclasses49() { parsingFixture.get().doCodeTest("a[+*?]b"); }
  @Test public void testCharclasses50() { parsingFixture.get().doCodeTest("a[\\p{IsDigit}\\p{IsAlpha}]b"); }
  @Test public void testCharclasses51() { parsingFixture.get().doCodeTest("[\\p{L}&&[^\\p{Lu}]]"); }
  @Test public void testCharclasses52() { parsingFixture.get().doCodeTest("\\pL\\pM\\pZ\\pS\\pN\\pP\\pC\\PL\\PM\\PZ\\PS\\PN\\PP\\PC"); }
  @Test public void testCharclasses53() { parsingFixture.get().doCodeTest("\\pA"); }
  @Test public void testCharclasses54() { parsingFixture.get().doCodeTest("\\pl"); }
  @Test public void testCharclasses55() { parsingFixture.get().doCodeTest("a\\p"); }
  @Test public void testCharclasses56() { parsingFixture.get().doCodeTest("a\\p{}"); }
  @Test public void testCharclasses57() { parsingFixture.get().doCodeTest("a\\p}"); }
  @Test public void testCharclasses58() { parsingFixture.get().doCodeTest("a\\p{123}"); }
  @Test public void testCharclasses59() { parsingFixture.get().doCodeTest("[\\p{nothing}]"); }
  @Test public void testCharclasses60() { parsingFixture.get().doCodeTest("a\\p{*}b"); }
  @Test public void testCharclasses61() { parsingFixture.get().doCodeTest("[\\w-\\w]"); }
  @Test public void testCharclasses62() { parsingFixture.get().doCodeTest("[a-\\w]"); }
  @Test public void testCharclasses63() { parsingFixture.get().doCodeTest("(?x)abc #foo \\q bar\n# foo\n(?-xi)xyz(?i:ABC)"); }
  @Test public void testCharclasses64() { parsingFixture.get().doCodeTest("[\\ud800\\udc00-\\udbff\\udfff]"); }
  @Test public void testCharclasses65() { parsingFixture.get().doCodeTest("\\R"); }
  @Test public void testCharclasses66() { parsingFixture.get().doCodeTest("\\X"); }
  @Test public void testCharclasses67() { parsingFixture.get().doCodeTest("\\-[\\*\\-\\[\\]\\\\\\+]"); }
  @Test public void testCharclasses68() { parsingFixture.get().doCodeTest("[\\b]"); }
  @Test public void testCharClasses69() { parsingFixture.get().doCodeTest("\\p{^L}"); }
  @Test public void testCharClasses70() { parsingFixture.get().doCodeTest("[&&&&a]"); }
  @Test public void testCharClasses71() { parsingFixture.get().doCodeTest("[a-\\Qz\\E]"); }
  @Test public void testCharClasses72() { parsingFixture.get().doCodeTest("([\\^])"); }
  @Test public void testCharClasses73() { parsingFixture.get().doCodeTest("[i-[:]]*"); }
  @Test public void testCharClasses74() { parsingFixture.get().doCodeTest("[\\w-z]"); }

  @Test public void testGroups1() { parsingFixture.get().doCodeTest("()ef"); }
  @Test public void testGroups2() { parsingFixture.get().doCodeTest("()*"); }
  @Test public void testGroups3() { parsingFixture.get().doCodeTest("()"); }
  @Test public void testGroups4() { parsingFixture.get().doCodeTest("(|)"); }
  @Test public void testGroups5() { parsingFixture.get().doCodeTest("(*)b"); }
  @Test public void testGroups6() { parsingFixture.get().doCodeTest("((a))"); }
  @Test public void testGroups7() { parsingFixture.get().doCodeTest("(a)b(c)"); }
  @Test public void testGroups8() { parsingFixture.get().doCodeTest("(a*)*"); }
  @Test public void testGroups9() { parsingFixture.get().doCodeTest("(a*)+"); }
  @Test public void testGroups10() { parsingFixture.get().doCodeTest("(a|)*"); }
  @Test public void testGroups11() { parsingFixture.get().doCodeTest("(ab|cd)e"); }
  @Test public void testGroups12() { parsingFixture.get().doCodeTest("(.*)c(.*)"); }
  @Test public void testGroups13() { parsingFixture.get().doCodeTest("\\((.*), (.*)\\)"); }
  @Test public void testGroups14() { parsingFixture.get().doCodeTest("a(bc)d"); }
  @Test public void testGroups15() { parsingFixture.get().doCodeTest("([abc])*d"); }
  @Test public void testGroups16() { parsingFixture.get().doCodeTest("((((((((((a)))))))))"); }
  @Test public void testGroups17() { parsingFixture.get().doCodeTest("([abc])*bcd"); }
  @Test public void testGroups18() { parsingFixture.get().doCodeTest("(a|b)c*d"); }
  @Test public void testGroups19() { parsingFixture.get().doCodeTest("a([bc]*)c*"); }
  @Test public void testGroups20() { parsingFixture.get().doCodeTest("((a)(b)c)(d)"); }
  @Test public void testGroups21() { parsingFixture.get().doCodeTest("(ab|a)b*c"); }
  @Test public void testGroups22() { parsingFixture.get().doCodeTest("(ab|ab*)bc"); }
  @Test public void testGroups23() { parsingFixture.get().doCodeTest("(a|b|c|d|e)f"); }
  @Test public void testGroups24() { parsingFixture.get().doCodeTest("a([bc]*)(c*d)"); }
  @Test public void testGroups25() { parsingFixture.get().doCodeTest("a([bc]+)(c*d)"); }
  @Test public void testGroups26() { parsingFixture.get().doCodeTest("a([bc]*)(c+d)"); }
  @Test public void testGroups27() { parsingFixture.get().doCodeTest("(a+|b)*"); }
  @Test public void testGroups28() { parsingFixture.get().doCodeTest("(a+|b)+"); }
  @Test public void testGroups29() { parsingFixture.get().doCodeTest("(a+|b)?"); }
  @Test public void testGroups30() { parsingFixture.get().doCodeTest("(^*"); }
  @Test public void testGroups31() { parsingFixture.get().doCodeTest(")("); }
  @Test public void testGroups32() { parsingFixture.get().doCodeTest("(?i:*)"); }
  @Test public void testGroups33() { parsingFixture.get().doCodeTest("(?<asdf>[a-c])\\1"); }
  @Test public void testGroups34() { parsingFixture.get().doCodeTest("(?<asdf>[a-c])\\k<asdf>"); }
  @Test public void testGroups35() { parsingFixture.get().doCodeTest("\\k<adsf>"); }
  @Test public void testGroups36() { parsingFixture.get().doCodeTest("(?P<name>{"); }
  @Test public void testGroups37() { parsingFixture.get().doCodeTest("(?P=name)"); }
  @Test public void testGroups38() { parsingFixture.get().doCodeTest("\\g'name'"); }
  @Test public void testGroups39() { parsingFixture.get().doCodeTest("(?(name)yes-pattern|no-pattern)"); }
  @Test public void testGroups40() { parsingFixture.get().doCodeTest("(?(name)yes-pattern|{"); }
  @Test public void testGroups41() { parsingFixture.get().doCodeTest("(?>atomic)"); }
  @Test public void testGroups42() { parsingFixture.get().doCodeTest("(?:non-capturing)"); }
  @Test public void testGroups43() { parsingFixture.get().doCodeTest("(?(name)yes-pattern|no_pattern|maybe-pattern)"); }

  @Test public void testEscapes1() { parsingFixture.get().doCodeTest("\\q"); }
  @Test public void testEscapes2() { parsingFixture.get().doCodeTest("\\#"); }
  @Test public void testEscapes3() { parsingFixture.get().doCodeTest("a\\"); }
  @Test public void testEscapes4() { parsingFixture.get().doCodeTest("a\\(b"); }
  @Test public void testEscapes5() { parsingFixture.get().doCodeTest("a\\(*b"); }
  @Test public void testEscapes6() { parsingFixture.get().doCodeTest("a\\\\b"); }
  @Test public void testEscapes7() { parsingFixture.get().doCodeTest("\\u004a"); }
  @Test public void testEscapes8() { parsingFixture.get().doCodeTest("\\0123"); }
  @Test public void testEscapes9() { parsingFixture.get().doCodeTest("\\0"); }
  @Test public void testEscapes10() { parsingFixture.get().doCodeTest("\\x4a"); }
  @Test public void testEscapes11() { parsingFixture.get().doCodeTest("\\x{0}"); }
  @Test public void testEscapes12() { parsingFixture.get().doCodeTest("\\x{2011F}"); }
  @Test public void testEscapes13() { parsingFixture.get().doCodeTest("[\\x4a-\\x4b]"); }
  @Test public void testEscapes14() { parsingFixture.get().doCodeTest("[a-a]"); }
  @Test public void testEscapes15() { parsingFixture.get().doCodeTest("[\\x4a-\\x3f]"); }
  @Test public void testEscapes16() { parsingFixture.get().doCodeTest("[\\udbff\\udfff-\\ud800\\udc00]"); }
  @Test public void testEscapes17() { parsingFixture.get().doCodeTest("[\\ud800\\udc00-\\udbff\\udfff]"); }
  @Test public void testEscapes18() { parsingFixture.get().doCodeTest("[z-a]"); }
  @Test public void testEscapes19() { parsingFixture.get().doCodeTest("[a-z]"); }
  @Test public void testEscapes20() { parsingFixture.get().doCodeTest("a\\Qabc?*+.))]][]\\Eb"); }
  @Test public void testEscapes21() { parsingFixture.get().doCodeTest("(a\\Qabc?*+.))]][]\\Eb)"); }
  @Test public void testEscapes22() { parsingFixture.get().doCodeTest("[\\Qabc?*+.))]][]\\E]"); }
  @Test public void testEscapes23() { parsingFixture.get().doCodeTest("a\\Qabc?*+.))]][]\\E)"); }
  @Test public void testEscapes24() { parsingFixture.get().doCodeTest("\\Q\\j\\E"); }
  @Test public void testEscapes25() { parsingFixture.get().doCodeTest("\\c0"); }
  @Test public void testEscapes26() { parsingFixture.get().doCodeTest("[\\]]"); }
  @Test public void testEscapes27() { parsingFixture.get().doCodeTest("[^\\]]"); }
  @Test public void testEscapes28() { parsingFixture.get().doCodeTest("[a\\]]"); }
  @Test public void testEscapes29() { parsingFixture.get().doCodeTest("[^a\\]]"); }
  @Test public void testEscapes30() { parsingFixture.get().doCodeTest("\\[\\]$"); }
  @Test public void testEscapes31() { parsingFixture.get().doCodeTest("\\ud800"); }

  @Test public void testAnchors1() { parsingFixture.get().doCodeTest("^*"); }
  @Test public void testAnchors2() { parsingFixture.get().doCodeTest("$*"); }
  @Test public void testAnchors3() { parsingFixture.get().doCodeTest("^abc"); }
  @Test public void testAnchors4() { parsingFixture.get().doCodeTest("^abc$"); }
  @Test public void testAnchors5() { parsingFixture.get().doCodeTest("abc$"); }
  @Test public void testAnchors6() { parsingFixture.get().doCodeTest("^"); }
  @Test public void testAnchors7() { parsingFixture.get().doCodeTest("$"); }
  @Test public void testAnchors8() { parsingFixture.get().doCodeTest("$b"); }
  @Test public void testAnchors9() { parsingFixture.get().doCodeTest("^(ab|cd)e"); }
  @Test public void testAnchors10() { parsingFixture.get().doCodeTest("^a(bc+|b[eh])g|.h$"); }

  @Test public void testNamedchars1() { parsingFixture.get().doCodeTest("a*b\\s+c"); }
  @Test public void testNamedchars2() { parsingFixture.get().doCodeTest("\\d+"); }
  @Test public void testNamedchars3() { parsingFixture.get().doCodeTest("^\\p{javaJavaIdentifierStart}+\\p{javaJavaIdentifierPart}+$"); }
  @Test public void testNamedchars4() { parsingFixture.get().doCodeTest("\\p{IsDigit}\\p{IsAlpha}"); }
  @Test public void testNamedchars5() { parsingFixture.get().doCodeTest("\\p{InLATIN_1_SUPPLEMENT}"); }
  @Test public void testNamedchars6() { parsingFixture.get().doCodeTest("[a-e]?d\\\\e"); }
  @Test public void testNamedchars7() { parsingFixture.get().doCodeTest("((\\w+)/)*(\\w+)"); }
  @Test public void testNamedchars8() { parsingFixture.get().doCodeTest("\\p{Digit}+"); }
  @Test public void testNamedchars9() { parsingFixture.get().doCodeTest("[:xdigit:]+"); }
  @Test public void testNamedchars10() { parsingFixture.get().doCodeTest("\\p{unknown}+"); }
  @Test public void testNamedchars11() { parsingFixture.get().doCodeTest("[:^xdigit:]+"); }
  @Test public void testNamedchars12() { parsingFixture.get().doCodeTest("\\p{InArabic Extended-A}"); }
  @Test public void testNamedchars13() { parsingFixture.get().doCodeTest("\\N{Mahjong Tile Winter}"); }
  @Test public void testNamedchars14() { parsingFixture.get().doCodeTest("[\\N{Mahjong Tile Winter}]"); }
  @Test public void testNamedchars15() { parsingFixture.get().doCodeTest("[\\N{LATIN SMALL LETTER A}-\\N{LATIN SMALL LETTER Z}]"); }

  @Test public void testBackrefs1() { parsingFixture.get().doCodeTest("(ac*)c*d[ac]*\\1"); }
  @Test public void testBackrefs2() { parsingFixture.get().doCodeTest("(.)=\\1"); }
  @Test public void testBackrefs3() { parsingFixture.get().doCodeTest("([ab])=\\1"); }
  @Test public void testBackrefs4() { parsingFixture.get().doCodeTest("([ab]+)=\\1"); }
  @Test public void testBackrefs5() { parsingFixture.get().doCodeTest("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)\\105"); }
  @Test public void testBackrefs6() { parsingFixture.get().doCodeTest("(.)\\11"); }
  @Test public void testBackrefs7() { parsingFixture.get().doCodeTest("([ab]+)=\\2"); }
  @Test public void testBackrefs8() { parsingFixture.get().doCodeTest("([ab]+)=\\3"); }
  @Test public void testBackrefs9() { parsingFixture.get().doCodeTest("([ab]+=\\1)"); }

  @Test public void testComplex1() { parsingFixture.get().doCodeTest("z(\\w\\s+(?:\\w\\s+\\w)+)z"); }
  @Test public void testComplex2() { parsingFixture.get().doCodeTest("(([hH][tT]{2}[pP]|[fF][tT][pP]):\\/\\/)?[a-zA-Z0-9\\-]+(\\.[a-zA-Z0-9\\-]+)*"); }
  @Test public void testComplex3() { parsingFixture.get().doCodeTest("((?:[hH][tT]{2}[pP]|[fF][tT][pP]):\\/\\/)?[a-zA-Z0-9\\-]+(\\.[a-zA-Z0-9\\-]+)*"); }
  @Test public void testComplex4() { parsingFixture.get().doCodeTest("(([hH][tT]{2}[pP]|[fF][tT][pP]):\\/\\/)?[a-zA-Z0-9\\-]+(?:\\.[a-zA-Z0-9\\-]+)*"); }
  @Test public void testComplex5() { parsingFixture.get().doCodeTest("(?:([hH][tT]{2}[pP]|[fF][tT][pP]):\\/\\/)?[a-zA-Z0-9\\-]+(\\.[a-zA-Z0-9\\-]+)*"); }
  @Test public void testComplex6() { parsingFixture.get().doCodeTest("^(?:([hH][tT]{2}[pP]|[fF][tT][pP]):\\/\\/)?[a-zA-Z0-9\\-]+(\\.[a-zA-Z0-9\\-]+)*$"); }
  @Test public void testComplex7() { parsingFixture.get().doCodeTest("^(?:(?:[hH][tT]{2}[pP]|[fF][tT][pP]):\\/\\/)?[a-zA-Z0-9\\-]+(?:\\.[a-zA-Z0-9\\-]+)*$"); }

  @Test public void testIncomplete1() { parsingFixture.get().doCodeTest("abc\\"); }
  @Test public void testIncomplete2() { parsingFixture.get().doCodeTest("abc[\\"); }
  @Test public void testIncomplete3() { parsingFixture.get().doCodeTest("abc\\x"); }
  @Test public void testIncomplete4() { parsingFixture.get().doCodeTest("abc\\x1"); }
  @Test public void testIncomplete5() { parsingFixture.get().doCodeTest("abc\\x{"); }
  @Test public void testIncomplete6() { parsingFixture.get().doCodeTest("abc\\x{}"); }
  @Test public void testIncomplete7() { parsingFixture.get().doCodeTest("abc\\x{0"); }
  @Test public void testIncomplete8() { parsingFixture.get().doCodeTest("abc\\u"); }
  @Test public void testIncomplete9() { parsingFixture.get().doCodeTest("abc\\u22"); }
  @Test public void testIncomplete10() { parsingFixture.get().doCodeTest("\\Qabc"); }
  @Test public void testIncomplete11() { parsingFixture.get().doCodeTest("\\Q"); }
  @Test public void testIncomplete12() { parsingFixture.get().doCodeTest("\\E"); }
  @Test public void testIncomplete13() { parsingFixture.get().doCodeTest("a|*"); }

  @Test public void testRegressions1() { parsingFixture.get().doCodeTest("("); }
  @Test public void testRegressions2() { parsingFixture.get().doCodeTest("[^^]"); }
  @Test public void testRegressions3() { parsingFixture.get().doCodeTest("a)b"); }
  @Test public void testRegressions4() { parsingFixture.get().doCodeTest("\\s*@return(?:s)?\\s*(?:(?:\\{|:)?\\s*(?([^\\s\\}]+)\\s*\\}?\\s*)?(.*)"); }

  @Test public void testOptions1() { parsingFixture.get().doCodeTest("(?iZm)abc"); }
  @Test public void testOptions2() { parsingFixture.get().doCodeTest("(?idmsuxU)nice"); }
  @Test public void testOptions3() { parsingFixture.get().doCodeTest("(?idm-suxU)one(?suxU-idm)two"); }
  @Test public void testOptions4() { parsingFixture.get().doCodeTest("(?i|abc"); }

  @Test public void testTests1() { parsingFixture.get().doCodeTest("abc)"); }
  @Test public void testTests2() { parsingFixture.get().doCodeTest("(abc"); }
  @Test public void testTests3() { parsingFixture.get().doCodeTest("a+b+c"); }
  @Test public void testTests4() { parsingFixture.get().doCodeTest("a**"); }
  @Test public void testTests5() { parsingFixture.get().doCodeTest("a++"); }
  @Test public void testTests6() { parsingFixture.get().doCodeTest("ab*"); }
  @Test public void testTests7() { parsingFixture.get().doCodeTest("abcd*efg"); }
  @Test public void testTests8() { parsingFixture.get().doCodeTest("a|b|c|d|e"); }
  @Test public void testTests9() { parsingFixture.get().doCodeTest("(bc+d$|ef*g.|h?i(j|k))"); }
  @Test public void testTests10() { parsingFixture.get().doCodeTest("a*(b*c*)"); }
  @Test public void testTests11() { parsingFixture.get().doCodeTest("a?b+c*"); }
  @Test public void testTests12() { parsingFixture.get().doCodeTest("i am a green (giant|man|martian)"); }
  @Test public void testTests13() { parsingFixture.get().doCodeTest("(wee|week)(knights|knight)"); }
  @Test public void testTests14() { parsingFixture.get().doCodeTest("(a.*b)(a.*b)"); }
  @Test public void testTests15() { parsingFixture.get().doCodeTest("(\\s*\\w+)?"); }
  @Test public void testTests16() { parsingFixture.get().doCodeTest("(?:a)"); }
  @Test public void testTests17() { parsingFixture.get().doCodeTest("(?:\\w)"); }
  @Test public void testTests18() { parsingFixture.get().doCodeTest("(?:\\w\\s\\w)+"); }
  @Test public void testTests19() { parsingFixture.get().doCodeTest("(a\\w)(?:,(a\\w))+"); }
  @Test public void testTests20() { parsingFixture.get().doCodeTest("abc.*?x+yz"); }
  @Test public void testTests21() { parsingFixture.get().doCodeTest("abc.+?x+yz"); }
  @Test public void testTests22() { parsingFixture.get().doCodeTest("a.+?(c|d)"); }
  @Test public void testTests23() { parsingFixture.get().doCodeTest("a.+(c|d)"); }
  @Test public void testTests24() { parsingFixture.get().doCodeTest("a+?b+?c+?"); }

  @Test public void testRealLife1() { parsingFixture.get().doCodeTest("x:found=\"(true|false)\""); }
  @Test public void testRealLife2() { parsingFixture.get().doCodeTest("(?:\\s)|(?:/\\*.*\\*/)|(?://[^\\n]*)"); }
  @Test public void testRealLife3() { parsingFixture.get().doCodeTest("((?:\\p{Alpha}\\:)?[0-9 a-z_A-Z\\-\\\\./]+)"); }
  @Test public void testRealLife4() { parsingFixture.get().doCodeTest("^[\\w\\+\\.\\-]{2,}:"); }
  @Test public void testRealLife5() { parsingFixture.get().doCodeTest("#(.*)$"); }
  @Test public void testRealLife6() { parsingFixture.get().doCodeTest("^(([^:]+)://)?([^:/]+)(:([0-9]+))?(/.*)"); }
  @Test public void testRealLife7() { parsingFixture.get().doCodeTest("(([^:]+)://)?([^:/]+)(:([0-9]+))?(/.*)"); }
  @Test public void testRealLife8() { parsingFixture.get().doCodeTest("usd [+-]?[0-9]+.[0-9][0-9]"); }
  @Test public void testRealLife9() { parsingFixture.get().doCodeTest("\\b(\\w+)(\\s+\\1)+\\b"); }
  @Test public void testRealLife10() { parsingFixture.get().doCodeTest(".*?(<(error|warning|info)(?: descr=\"((?:[^\"\\\\]|\\\\\")*)\")?(?: type=\"([0-9A-Z_]+)\")?(?: foreground=\"([0-9xa-f]+)\")?(?: background=\"([0-9xa-f]+)\")?(?: effectcolor=\"([0-9xa-f]+)\")?(?: effecttype=\"([A-Z]+)\")?(?: fonttype=\"([0-9]+)\")?(/)?>)(.*)"); }

  @Test public void testBug1() { parsingFixture.get().doCodeTest("[{][\\w\\.]*[}]"); }
  @Test public void testBug2() { parsingFixture.get().doCodeTest("[a-z0-9!\\#$%&'*+/=?^_`{|}~-]+"); }
  @Test public void testBug3() { parsingFixture.get().doCodeTest("[\\{]"); }
  @Test public void testBug4() { parsingFixture.get().doCodeTest("{"); }
  @Test public void testBug5() { parsingFixture.get().doCodeTest("\\{"); }
  @Test public void testBug6() { parsingFixture.get().doCodeTest("(<=\\s)-{3,}(?>\\s)"); }
  @Test public void testBug7() { parsingFixture.get().doCodeTest("(?x)a\\ b\\ c"); }
  @Test public void testBug8() { parsingFixture.get().doCodeTest("a\\ b"); }
  @Test public void testBug9() { parsingFixture.get().doCodeTest("(^|\\.)\\*(?=(\\.|$))"); }
  @Test public void testBug10() { parsingFixture.get().doCodeTest("\\h \\H \\v \\V"); }

  @Test public void testParse1() { parsingFixture.get().doCodeTest("123 | 456"); }
  @Test public void testParse2() { parsingFixture.get().doCodeTest("1**"); }
  @Test public void testParse3() { parsingFixture.get().doCodeTest("(([hH][tT]{2}[pP]|[fF][tT][pP])://)?[a-zA-Z0-9\\-]+(\\.[a-zA-Z0-9\\-]+)*"); }

  @Test public void testCategoryShorthand1() { parsingFixture.get().doCodeTest("\\pL"); }

  @Test
  public void testCapabilitiesProvider() {
    ParsingTestFixture fixture = parsingFixture.get();
    RegExpCapabilitiesProvider provider = (host, def) -> EnumSet.of(POSIX_BRACKET_EXPRESSIONS);
    fixture.addExplicitExtension(RegExpCapabilitiesProvider.EP, RegExpLanguage.INSTANCE, provider);
    PsiFile file = fixture.createPsiFile("c", "(?#xxx)");
    PsiFile injected = fixture.createPsiFile("a", "[[:blank:]]");
    fixture.setFile(injected);
    ReadAction.runBlocking(() -> {
      PsiComment context = SyntaxTraverser.psiTraverser(file).filter(PsiComment.class).first();
      SmartPsiElementPointer<PsiComment> pointer =
        SmartPointerManager.getInstance(fixture.getProject()).createSmartPsiElementPointer(context, file);
      injected.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, pointer);
      ParsingTestUtil.ensureParsed(injected);
    });
    fixture.checkResult(fixture.getFilePrefix() + fixture.getTestName(), injected);
  }
}
