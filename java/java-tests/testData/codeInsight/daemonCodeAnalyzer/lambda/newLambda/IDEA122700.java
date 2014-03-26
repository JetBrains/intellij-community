class Test {
  class Event {}
  class KeyEvent extends Event {
    int getCode() {
      return 0;
    }
  }
  interface EventListener<T extends Event> {
    void handle(T event);
  }

  class EventType<T extends Event>{}
  static final EventType<KeyEvent> KEY_PRESSED = null;

  {
    addEventHandler(KEY_PRESSED, keyEvent -> {
      int i = keyEvent.getCode();
    });

  }



  public final <T extends Event> void addEventHandler(final EventType<T> eventType, final EventListener<? super T> listener) {}

}