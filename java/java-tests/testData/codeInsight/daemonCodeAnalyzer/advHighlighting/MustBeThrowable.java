class c {
    void f1() {
        try {
        } catch (<error descr="Incompatible types. Found: 'java.lang.Error[]', required: 'java.lang.Throwable'">Error[] e</error>) {
        }
        try {
        } catch (<error descr="Incompatible types. Found: 'java.lang.Error[]', required: 'java.lang.Throwable'">Error e[]</error>) {
        }
        try {
        } catch (<error descr="Incompatible types. Found: 'java.lang.Error[][][][]', required: 'java.lang.Throwable'">Error[] []e[] []</error>) {
        }
        catch(<error descr="Incompatible types. Found: 'int', required: 'java.lang.Throwable'">int e</error>) {
        }

    }

}

class MyException // does not extend Throwable
{}
 
class a60
{
  public void test() throws <error descr="Incompatible types. Found: 'MyException', required: 'java.lang.Throwable'">MyException</error>
  {
    throw <error descr="Incompatible types. Found: 'MyException', required: 'java.lang.Throwable'">new MyException()</error>;
  }
  public void test(int i) {
	switch (i) {
		case 1: throw <error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Throwable'">false</error>;
		case 2: throw <error descr="Incompatible types. Found: 'int', required: 'java.lang.Throwable'">1</error>;
		case 3: throw <error descr="Incompatible types. Found: 'double', required: 'java.lang.Throwable'">1.0</error>;
		case 4: throw <error descr="Incompatible types. Found: 'char', required: 'java.lang.Throwable'">'a'</error>;
		case 5: throw <error descr="Incompatible types. Found: 'long', required: 'java.lang.Throwable'">1L</error>;
		case 6: throw <error descr="Incompatible types. Found: 'float', required: 'java.lang.Throwable'">1.0f</error>;
	}
  }
}

 class Contest {
    short midget;

    void strongMan() throws <error descr="Class name expected">midget</error> {
    }
}

