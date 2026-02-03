// IDEA-230097
class EscapeAnalysisLambdaInConstructor {
  public void incorrectInspection() {
    Callbackable callbackable = new Callbackable();
    EventListenerSetup listener = new EventListenerSetup();
    callbackable.setCallback(listener.listener);
    if (listener.listenerHasBeenCalled) {
      throw new AssertionError();
    }

    callbackable.callCallback();

    if (!listener.listenerHasBeenCalled) {
      throw new AssertionError();
    }
  }
}

class Callbackable {  
  Runnable callback;

  public void setCallback(Runnable callback) {
    this.callback = callback;
  }

  public void callCallback(){
    callback.run();
  }
}

class EventListenerSetup {
  final Runnable listener;
  boolean listenerHasBeenCalled = false;

  public EventListenerSetup() {
    this.listener = () -> listenerHasBeenCalled = true;
  }
}
