import java.util.NoSuchElementException;

public class MyQueue {
  private long[] items = new long[16];

  private int head;
  private int tail;

  public boolean isEmpty() { return head == tail; }

  public long getFirst() {
    if (isEmpty())
      throw new NoSuchElementException("Queue is empty");
    return items[head];
  }

  // true if the first element of the queue is greater than specified number
  public boolean firstIsGreaterThan(int x) {
    return !isEmpty() && g<caret>etFirst() > x;
  }

  // other methods
}