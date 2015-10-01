package com.intellij.unscramble;

import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;

/**
 * @author cdr
 */
public class NormalizeTextTest extends TestCase {
  public void testCausedBy() {
    doTest("""\
javax.faces.FacesException: Error calling action method of component with id _id6:_id10
        at java.lang.Th
read.run(Thread.java:626)
Caused by: javax.faces.el.EvaluationException: Exception while invoking expression #{_loginAction.login}
        at org.apache.myfaces.el.MethodBindingImpl.invoke(MethodBindingImpl.java:153)
        at org.apache.myfaces.application.ActionListenerImpl.processAction(ActionListenerImpl.java:63)
""",

           """\
javax.faces.FacesException: Error calling action method of component with id _id6:_id10
        at java.lang.Thread.run(Thread.java:626)
Caused by: javax.faces.el.EvaluationException: Exception while invoking expression #{_loginAction.login}
        at org.apache.myfaces.el.MethodBindingImpl.invoke(MethodBindingImpl.java:153)
        at org.apache.myfaces.application.ActionListenerImpl.processAction(ActionListenerImpl.java:63)"""
           );
  }

  public void testThreadNames() {
    doTest('''\
"Background process" prio=6 tid=0x21193b88 nid=0x11ea4 waiting on condition [0x2
2cbf000..0x22cbfd68]
        at java.lang.Thread.sleep(Native Method)
        at com.intellij.util.ui.Timer$1.run(Timer.java:23)\n
"Alarm pool" prio=6 tid=0x37cacbe0 nid=0x7940 waiting on condition [0x3972f000..
0x3972fae8]
        at sun.misc.Unsafe.park(Native Method)
        at java.util.concurrent.locks.LockSupport.park(LockSupport.java:118)
        at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject
.await(AbstractQueuedSynchronizer.java:1767)

        at java.util.concurrent.LinkedBlockingQueue.take(LinkedBlockingQueue.jav
a:359)
        at java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.ja
va:470)
        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor
.java:674)
        at java.lang.Thread.run(Thread.java:595)''',


           '''"\
Background process" prio=6 tid=0x21193b88 nid=0x11ea4 waiting on condition [0x22cbf000..0x22cbfd68]
        at java.lang.Thread.sleep(Native Method)
        at com.intellij.util.ui.Timer$1.run(Timer.java:23)

"Alarm pool" prio=6 tid=0x37cacbe0 nid=0x7940 waiting on condition [0x3972f000..0x3972fae8]
        at sun.misc.Unsafe.park(Native Method)
        at java.util.concurrent.locks.LockSupport.park(LockSupport.java:118)
        at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.await(AbstractQueuedSynchronizer.java:1767)
        at java.util.concurrent.LinkedBlockingQueue.take(LinkedBlockingQueue.java:359)
        at java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:470)
        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:674)
        at java.lang.Thread.run(Thread.java:595)'''
           );
  }

  public void testLocked() {
    doTest("""\
       at com.intellij.lang.jsp.JspFileViewProviderImpl.getKeyPrefixes(JspFileV
iewProviderImpl.java:76)
        at com.intellij.lang.jsp.JspFileViewProviderImpl.getKnownTaglibPrefixes(
JspFileViewProviderImpl.java:89)
        - locked <0x04cc0768> (a java.lang.Object)
        at com.intellij.psi.impl.source.parsing.jsp.JspLexer.doInitOriginal(JspL
exer.java:39)
        at com.intellij.psi.impl.source.parsing.jsp.JspLexer.start(JspLexer.java
:49)""",

           """\
       at com.intellij.lang.jsp.JspFileViewProviderImpl.getKeyPrefixes(JspFileViewProviderImpl.java:76)
        at com.intellij.lang.jsp.JspFileViewProviderImpl.getKnownTaglibPrefixes(JspFileViewProviderImpl.java:89)
        - locked <0x04cc0768> (a java.lang.Object)
        at com.intellij.psi.impl.source.parsing.jsp.JspLexer.doInitOriginal(JspLexer.java:39)
        at com.intellij.psi.impl.source.parsing.jsp.JspLexer.start(JspLexer.java:49)""");
  }
  public void testAtSplit() {
    doTest("""\
java.lang.Throwable
at
com.intellij.openapi.util.objectTree.ObjectNode.<init>(ObjectNode.jav
a:31)
at
com.intellij.openapi.util.objectTree.ObjectTree.getNodeFor(ObjectTree
.java:79)""",

           """\
java.lang.Throwable
at com.intellij.openapi.util.objectTree.ObjectNode.<init>(ObjectNode.java:31)
at com.intellij.openapi.util.objectTree.ObjectTree.getNodeFor(ObjectTree.java:79)""");
  }
  
  public void testSplitMergedLines() {
    doTest("org.apache.velocity.exception.MethodInvocationException " +
           "at org.apache.velocity.runtime.parser.node.ASTMethod.execute(ASTMethod.java:246) " +
           "at org.apache.velocity.runtime.parser.node.ASTReference.execute(ASTReference.java:175) " +
           "at org.apache.velocity.runtime.parser.node.ASTReference.value(ASTReference.java:327) " +
           "at org.apache.velocity.runtime.parser.node.ASTExpression.value(ASTExpression.java:51) " +
           "at org.apache.velocity.runtime.parser.node.ASTSetDirective.render(ASTSetDirective.java:95) " +
           "at org.apache.velocity.runtime.parser.node.ASTBlock.render(ASTBlock.java:55) " +
           "at org.apache.velocity.runtime.directive.Foreach.render(Foreach.java:166) " +
           "at org.apache.velocity.runtime.parser.node.ASTDirective.render(ASTDirective.java:114) " +
           "at org.apache.velocity.runtime.parser.node.SimpleNode.render(SimpleNode.java:230) " +
           "at org.apache.velocity.runtime.directive.VelocimacroProxy.render(VelocimacroProxy.java:172) " +
           "at org.apache.velocity.runtime.parser.node.ASTDirective.render(ASTDirective.java:114) " +
           "at org.apache.velocity.runtime.parser.node.SimpleNode.render(SimpleNode.java:230) " +
           "at org.apache.velocity.Template.merge(Template.java:256)",

           """\
org.apache.velocity.exception.MethodInvocationException
at org.apache.velocity.runtime.parser.node.ASTMethod.execute(ASTMethod.java:246)
at org.apache.velocity.runtime.parser.node.ASTReference.execute(ASTReference.java:175)
at org.apache.velocity.runtime.parser.node.ASTReference.value(ASTReference.java:327)
at org.apache.velocity.runtime.parser.node.ASTExpression.value(ASTExpression.java:51)
at org.apache.velocity.runtime.parser.node.ASTSetDirective.render(ASTSetDirective.java:95)
at org.apache.velocity.runtime.parser.node.ASTBlock.render(ASTBlock.java:55)
at org.apache.velocity.runtime.directive.Foreach.render(Foreach.java:166)
at org.apache.velocity.runtime.parser.node.ASTDirective.render(ASTDirective.java:114)
at org.apache.velocity.runtime.parser.node.SimpleNode.render(SimpleNode.java:230)
at org.apache.velocity.runtime.directive.VelocimacroProxy.render(VelocimacroProxy.java:172)
at org.apache.velocity.runtime.parser.node.ASTDirective.render(ASTDirective.java:114)
at org.apache.velocity.runtime.parser.node.SimpleNode.render(SimpleNode.java:230)
at org.apache.velocity.Template.merge(Template.java:256)""");
  }

  public void testWithoutAt() {
    doTest('''\
 java.util.concurrent.ForkJoinTask$AdaptedRunnableAction.exec(ForkJoinTask.java:1407)
 java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:289)
 java.util.concurrent.ForkJoinTask.doInvoke(ForkJoinTask.java:400)
 java.util.concurrent.ForkJoinTask.invokeAll(ForkJoinTask.java:837)
''',

           '''\
 java.util.concurrent.ForkJoinTask$AdaptedRunnableAction.exec(ForkJoinTask.java:1407)
 java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:289)
 java.util.concurrent.ForkJoinTask.doInvoke(ForkJoinTask.java:400)
 java.util.concurrent.ForkJoinTask.invokeAll(ForkJoinTask.java:837)''');
  }

  private static void doTest(@NonNls String stackTrace, @NonNls String expected) {
    String normalized = UnscrambleDialog.normalizeText(stackTrace);
    assertEquals(expected, normalized);
  }
}
