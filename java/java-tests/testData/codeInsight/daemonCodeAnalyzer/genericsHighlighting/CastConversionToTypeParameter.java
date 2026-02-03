import java.util.List;

abstract class Test implements List<Test> {
  private <T extends List<T>> T foo() {
    return (T)this;
  }
}