public interface A {
    void foo(String pppppppppppp1, String pppppppppppp2,String pppppppppppp3,String pppppppppppp4,String pppppppppppp5,String pppppppppppp6,String pppppppppppp7,String pppppppppppp8);
}

abstract class B implements A{
    public void foo(final String pppppppppppp1,
                    final String pppppppppppp2,
                    final String pppppppppppp3,
                    final String pppppppppppp4,
                    final String pppppppppppp5,
                    final String pppppppppppp6,
                    final String pppppppppppp7,
                    final String pppppppppppp8) {
        <caret>
    }
}