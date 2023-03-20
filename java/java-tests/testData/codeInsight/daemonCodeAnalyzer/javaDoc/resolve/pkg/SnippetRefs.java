package pkg;

/**
 * {@snippet file="Test.java"}
 * {@snippet class=Test}
 * {@snippet class=<error descr="Snippet file is not found">Test1</error>}
 * {@snippet class="Test"}
 * {@snippet class='Test''}
 * {@snippet class='sub.Test' region="reg"}
 * {@snippet class=<error descr="Snippet file is not found">'sub.Test2'</error>}
 * {@snippet file="sub/test.txt"}
 */
public class SnippetRefs {
}