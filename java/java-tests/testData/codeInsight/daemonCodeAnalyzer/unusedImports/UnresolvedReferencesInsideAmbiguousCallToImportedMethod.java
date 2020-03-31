import static a.A.foo;
class Test {
     {
          foo(<error descr="Cannot resolve method 'unresolvedMethodCall' in 'Test'">unresolvedMethodCall</error>());
     }
}