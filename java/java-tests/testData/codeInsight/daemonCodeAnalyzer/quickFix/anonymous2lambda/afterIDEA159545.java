// "Replace with lambda" "true"

class MyTest {
  void addEventListener(EventListener<? extends Event> listener) { }

  {
    addEventListener((EventListener<InputEvent>) event -> System.out.println(event.getValue()));
  }

}

interface Event { }

interface EventListener<E extends Event> {
  void onEvent(E event);
}

interface InputEvent extends Event {
  Object getValue();
}
