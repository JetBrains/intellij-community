
public class ContinueOrBreakFromFinallyBlock {

  public void one() {
    while (true) {
      try {

      } finally {
        <warning descr="'break' inside 'finally' block">break</warning>;
      }
    }
  }

  public void two() {
    while (true) {
      try {

      } finally {
        <warning descr="'continue' inside 'finally' block">continue</warning>;
      }
    }
  }

  public void three() {
    try {

    } finally {
      while (true) {
        break;
      }
    }
  }
}