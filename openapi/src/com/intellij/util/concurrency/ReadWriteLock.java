/*
  File: ReadWriteLock.java

  Originally written by Doug Lea and released into the public domain.
  This may be used for any purposes whatsoever without acknowledgment.
  Thanks for the assistance and support of Sun Microsystems Labs,
  and everyone contributing, testing, and using this code.

  History:
  Date       Who                What
  11Jun1998  dl               Create public version
*/


package com.intellij.util.concurrency;

/**
 *  ReadWriteLocks maintain a pair of associated locks.
 * The readLock may be held simultanously by multiple
 * reader threads, so long as there are no writers. The writeLock
 * is exclusive. ReadWrite locks are generally preferable to
 * plain Sync locks or synchronized methods in cases where:
 * <ul>
 *   <li> The methods in a class can be cleanly separated into
 *        those that only access (read) data vs those that 
 *        modify (write).
 *   <li> Target applications generally have more readers than writers.
 *   <li> The methods are relatively time-consuming (as a rough
 *        rule of thumb, exceed more than a hundred instructions), so it
 *        pays to introduce a bit more overhead associated with
 *        ReadWrite locks compared to simple synchronized methods etc
 *        in order to allow concurrency among reader threads.
 *        
 * </ul>
 * Different implementation classes differ in policies surrounding
 * which threads to prefer when there is
 * contention. By far, the most commonly useful policy is 
 * WriterPreferenceReadWriteLock. The other implementations
 * are targeted for less common, niche applications.
 *<p>
 * Standard usage:
 * <pre>
 * class X {
 *   ReadWriteLock rw;
 *   // ...
 *
 *   public void read() throws InterruptedException { 
 *     rw.readLock().acquire();
 *     try {
 *       // ... do the read
 *     }
 *     finally {
 *       rw.readlock().release()
 *     }
 *   }
 *
 *
 *   public void write() throws InterruptedException { 
 *     rw.writeLock().acquire();
 *     try {
 *       // ... do the write
 *     }
 *     finally {
 *       rw.writelock().release()
 *     }
 *   }
 * }
 * </pre>
 * @see Sync
 * <p>[<a href="http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/intro.html"> Introduction to this package. </a>]

**/

public interface ReadWriteLock {
  /** get the readLock **/
  Sync readLock();

  /** get the writeLock **/
  Sync writeLock();
}

