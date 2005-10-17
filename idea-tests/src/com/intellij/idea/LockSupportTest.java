package com.intellij.idea;

import com.intellij.idea.SocketLock;
import junit.framework.TestCase;

/**
 * @author mike
 */
public class LockSupportTest extends TestCase {
  public void testLock() throws Exception {
    final SocketLock lock = new SocketLock();
    assertTrue(lock.lock("abc"));
    lock.dispose();
  }

  public void testTwoLocks() throws Exception {
    final SocketLock lock1 = new SocketLock();
    final SocketLock lock2 = new SocketLock();

    assertTrue(lock1.lock("1"));
    assertTrue(lock1.lock("1.1"));
    assertTrue(lock2.lock("2"));
    assertTrue(!lock1.lock("2"));
    assertTrue(!lock2.lock("1"));
    assertTrue(!lock2.lock("1.1"));

    lock1.dispose();
    lock2.dispose();
  }

  public void testDispose() throws Exception {
    final SocketLock lock1 = new SocketLock();
    final SocketLock lock2 = new SocketLock();

    assertTrue(lock1.lock("1"));
    assertTrue(!lock2.lock("1"));

    lock1.dispose();
    assertTrue(lock2.lock("1"));
    lock2.dispose();
  }

  public void testUnlock() throws Exception {
    final SocketLock lock1 = new SocketLock();
    final SocketLock lock2 = new SocketLock();

    assertTrue(lock1.lock("1"));
    assertTrue(!lock2.lock("1"));

    lock1.unlock("1");
    assertTrue(lock2.lock("1"));
    lock1.dispose();
    lock2.dispose();
  }
}
