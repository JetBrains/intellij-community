/*
 * @author max
 */
package com.intellij.util.messages;

import com.intellij.openapi.util.text.StringUtil;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

public class MessageBusTest extends TestCase {
  private MessageBus myBus;
  private List<String> myLog;

  public interface T1Listener {
    void t11();
    void t12();
  }

  public interface T2Listener {
    void t21();
    void t22();
  }

  private final static Topic<T1Listener> T1 = new Topic<T1Listener>("T1", T1Listener.class);
  private final static Topic<T2Listener> T2 = new Topic<T2Listener>("T1", T2Listener.class);

  private class T1Handler implements T1Listener {
    private final String id;

    public T1Handler(final String id) {
      this.id = id;
    }

    public void t11() {
      myLog.add(id + ":" + "t11");
    }

    public void t12() {
      myLog.add(id + ":" + "t12");
    }
  }
  private class T2Handler implements T2Listener {
    private final String id;

    public T2Handler(final String id) {
      this.id = id;
    }

    public void t21() {
      myLog.add(id + ":" + "t21");
    }

    public void t22() {
      myLog.add(id + ":" + "t22");
    }
  }


  protected void setUp() throws Exception {
    super.setUp();
    myBus = MessageBusFactory.newMessageBus(this);
    myLog = new ArrayList<String>();
  }

  public void testNoListenersSubscribed() {
    myBus.syncPublisher(T1).t11();
    assertEvents();
  }

  public void testSingleMessage() {
    final MessageBusConnection connection = myBus.connect();
    connection.subscribe(T1, new T1Handler("c"));
    myBus.syncPublisher(T1).t11();
    assertEvents("c:t11");
  }

  public void testSingleMessageToTwoConnections() {
    final MessageBusConnection c1 = myBus.connect();
    c1.subscribe(T1, new T1Handler("c1"));

    final MessageBusConnection c2 = myBus.connect();
    c2.subscribe(T1, new T1Handler("c2"));

    myBus.syncPublisher(T1).t11();
    assertEvents("c1:t11", "c2:t11");
  }

  public void testTwoMessagesWithSingleSubscription() {
    final MessageBusConnection connection = myBus.connect();
    connection.subscribe(T1, new T1Handler("c"));
    myBus.syncPublisher(T1).t11();
    myBus.syncPublisher(T1).t12();

    assertEvents("c:t11", "c:t12");
  }

  public void testTwoMessagesWithDoubleSubscription() {
    final MessageBusConnection c1 = myBus.connect();
    c1.subscribe(T1, new T1Handler("c1"));

    final MessageBusConnection c2 = myBus.connect();
    c2.subscribe(T1, new T1Handler("c2"));

    myBus.syncPublisher(T1).t11();
    myBus.syncPublisher(T1).t12();

    assertEvents("c1:t11", "c2:t11", "c1:t12", "c2:t12");
  }

  public void testEventFiresAnotherEvent() {
    final MessageBusConnection c1 = myBus.connect();
    c1.subscribe(T1, new T1Listener() {
      public void t11() {
        myLog.add("c1:t11");
        myBus.syncPublisher(T2).t21();
        myLog.add("c1:t11:done");
      }

      public void t12() {
        myLog.add("c1:t12");
      }
    });

    final MessageBusConnection c2 = myBus.connect();
    c2.subscribe(T1, new T1Handler("c2"));
    c2.subscribe(T2, new T2Handler("c2"));

    myBus.syncPublisher(T1).t12();
    myBus.syncPublisher(T1).t11();

    assertEvents("c1:t12", "c2:t12", "c1:t11", "c2:t11", "c2:t21", "c1:t11:done");
  }

  public void testConnectionTerminatedInDispatch() {
    final MessageBusConnection c1 = myBus.connect();
    c1.subscribe(T1, new T1Listener() {
      public void t11() {
        c1.disconnect();
        myLog.add("c1:t11");
        myBus.syncPublisher(T2).t21();
        myLog.add("c1:t11:done");
      }

      public void t12() {
        myLog.add("c1:t12");
      }
    });
    c1.subscribe(T2, new T2Handler("c1"));

    final MessageBusConnection c2 = myBus.connect();
    c2.subscribe(T1, new T1Handler("c2"));
    c2.subscribe(T2, new T2Handler("c2"));

    myBus.syncPublisher(T1).t11();
    myBus.syncPublisher(T1).t12();

    assertEvents("c1:t11", "c2:t11", "c2:t21", "c1:t11:done", "c2:t12");
  }
  
  private void assertEvents(String... expected) {
    String joinExpected = StringUtil.join(expected, "\n");
    String joinActual = StringUtil.join(myLog.toArray(new String[0]), "\n");

    assertEquals("events mismatch", joinExpected, joinActual);
  }


}
