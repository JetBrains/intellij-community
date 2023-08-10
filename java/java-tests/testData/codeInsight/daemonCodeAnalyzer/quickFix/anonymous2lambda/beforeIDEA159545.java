// "Replace with lambda" "true-preview"

class MyTest {
  void addEventListener(EventListener<? extends Event> listener) { }

  {
    addEventListener(new Event<caret>Listener<InputEvent>() {
      @Override
      public void onEvent(InputEvent event) {
        System.out.println(event.getValue());
      }
    });
  }

}

interface Event { }

interface EventListener<E extends Event> {
  void onEvent(E event);
}

interface InputEvent extends Event {
  Object getValue();
}
