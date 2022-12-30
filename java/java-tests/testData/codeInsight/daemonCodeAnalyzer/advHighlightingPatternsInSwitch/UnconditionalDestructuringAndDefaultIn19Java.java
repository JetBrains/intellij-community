record RecordInterface(I x, I y) {}

sealed interface I permits C, D {}
final class C implements I {}
final class D implements I {}

public class Totality {

  RecordInterface recordInterface;

  void test(){
    switch (recordInterface){
      case <error descr="'switch' has both a total pattern and a default label">RecordInterface(I x, I y)</error> -> {}
      case <error descr="'switch' has both a total pattern and a default label">default</error> -> {}
    }
    switch (recordInterface){
        case <error descr="'switch' has both a total pattern and a default label">RecordInterface(I x, I y) r when true</error>-> {}
        case <error descr="'switch' has both a total pattern and a default label">default</error> -> {}
    }
    switch (recordInterface){
        case RecordInterface(I x, I y) -> {}
    }
    switch (recordInterface){
        case <error descr="'switch' has both a total pattern and a default label">RecordInterface r</error> -> {}
        <error descr="'switch' has both a total pattern and a default label">default</error> -> {}
    }
    switch (recordInterface){
        case RecordInterface(I x, C y) -> {}
        case default -> {}
    }
  }
}
