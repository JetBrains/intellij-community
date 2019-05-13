class B<S, R> {}
abstract class A<T>  {


  <K> void baz1(B<K, K> a) {}
  abstract B<T,T> foo1();
  void bar1(A<?> a){
    baz1(a.foo1());
  }


  <K> void baz2(B<K, K> a) {}
  abstract B<T,T> foo2();
  void bar2(A<? super T> a){
    baz2(a.foo2());
  }


  <K> void baz3(B<K, K> a) {}
  abstract B<T,T> foo3();
  void bar3(A<? extends T> a){
    baz3(a.foo3());
  }


  <K> void baz4(B<K, K> a) {}
  abstract B<T,? super T> foo4();
  void bar4(A<?> a){
    baz4<error descr="'baz4(B<K,K>)' in 'A' cannot be applied to '(B<capture<?>,capture<? super capture<?>>>)'">(a.foo4())</error>;
  }


  <K> void baz5(B<K, K> a) {}
  abstract B<T,? super T> foo5();
  void bar5(A<? super T> a){
    baz5<error descr="'baz5(B<K,K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? super T>>)'">(a.foo5())</error>;
  }


  <K> void baz6(B<K, K> a) {}
  abstract B<T,? super T> foo6();
  void bar6(A<? extends T> a){
    baz6<error descr="'baz6(B<K,K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? super capture<? extends T>>>)'">(a.foo6())</error>;
  }


  <K> void baz7(B<K, K> a) {}
  abstract B<T,? extends T> foo7();
  void bar7(A<?> a){
    baz7<error descr="'baz7(B<K,K>)' in 'A' cannot be applied to '(B<capture<?>,capture<? extends capture<?>>>)'">(a.foo7())</error>;
  }


  <K> void baz8(B<K, K> a) {}
  abstract B<T,? extends T> foo8();
  void bar8(A<? super T> a){
    baz8<error descr="'baz8(B<K,K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? extends capture<? super T>>>)'">(a.foo8())</error>;
  }


  <K> void baz9(B<K, K> a) {}
  abstract B<T,? extends T> foo9();
  void bar9(A<? extends T> a){
    baz9<error descr="'baz9(B<K,K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends T>>)'">(a.foo9())</error>;
  }


  <K> void baz10(B<K, K> a) {}
  abstract B<T,?> foo10();
  void bar10(A<?> a){
    baz10<error descr="'baz10(B<K,K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo10())</error>;
  }


  <K> void baz11(B<K, K> a) {}
  abstract B<T,?> foo11();
  void bar11(A<? super T> a){
    baz11<error descr="'baz11(B<K,K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<?>>)'">(a.foo11())</error>;
  }


  <K> void baz12(B<K, K> a) {}
  abstract B<T,?> foo12();
  void bar12(A<? extends T> a){
    baz12<error descr="'baz12(B<K,K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<?>>)'">(a.foo12())</error>;
  }


  <K> void baz13(B<K, K> a) {}
  abstract B<? super T,? super T> foo13();
  void bar13(A<?> a){
    baz13<error descr="'baz13(B<K,K>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<? super capture<?>>>)'">(a.foo13())</error>;
  }


  <K> void baz14(B<K, K> a) {}
  abstract B<? super T,? super T> foo14();
  void bar14(A<? super T> a){
    baz14<error descr="'baz14(B<K,K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? super T>>)'">(a.foo14())</error>;
  }


  <K> void baz15(B<K, K> a) {}
  abstract B<? super T,? super T> foo15();
  void bar15(A<? extends T> a){
    baz15<error descr="'baz15(B<K,K>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<? super capture<? extends T>>>)'">(a.foo15())</error>;
  }


  <K> void baz16(B<K, K> a) {}
  abstract B<? super T,? extends T> foo16();
  void bar16(A<?> a){
    baz16<error descr="'baz16(B<K,K>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<? extends capture<?>>>)'">(a.foo16())</error>;
  }


  <K> void baz17(B<K, K> a) {}
  abstract B<? super T,? extends T> foo17();
  void bar17(A<? super T> a){
    baz17<error descr="'baz17(B<K,K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? extends capture<? super T>>>)'">(a.foo17())</error>;
  }


  <K> void baz18(B<K, K> a) {}
  abstract B<? super T,? extends T> foo18();
  void bar18(A<? extends T> a){
    baz18<error descr="'baz18(B<K,K>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<? extends T>>)'">(a.foo18())</error>;
  }


  <K> void baz19(B<K, K> a) {}
  abstract B<? super T,?> foo19();
  void bar19(A<?> a){
    baz19<error descr="'baz19(B<K,K>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<?>>)'">(a.foo19())</error>;
  }


  <K> void baz20(B<K, K> a) {}
  abstract B<? super T,?> foo20();
  void bar20(A<? super T> a){
    baz20<error descr="'baz20(B<K,K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<?>>)'">(a.foo20())</error>;
  }


  <K> void baz21(B<K, K> a) {}
  abstract B<? super T,?> foo21();
  void bar21(A<? extends T> a){
    baz21<error descr="'baz21(B<K,K>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<?>>)'">(a.foo21())</error>;
  }


  <K> void baz22(B<K, K> a) {}
  abstract B<? extends T,? extends T> foo22();
  void bar22(A<?> a){
    baz22<error descr="'baz22(B<K,K>)' in 'A' cannot be applied to '(B<capture<? extends capture<?>>,capture<? extends capture<?>>>)'">(a.foo22())</error>;
  }


  <K> void baz23(B<K, K> a) {}
  abstract B<? extends T,? extends T> foo23();
  void bar23(A<? super T> a){
    baz23<error descr="'baz23(B<K,K>)' in 'A' cannot be applied to '(B<capture<? extends capture<? super T>>,capture<? extends capture<? super T>>>)'">(a.foo23())</error>;
  }


  <K> void baz24(B<K, K> a) {}
  abstract B<? extends T,? extends T> foo24();
  void bar24(A<? extends T> a){
    baz24<error descr="'baz24(B<K,K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends T>>)'">(a.foo24())</error>;
  }


  <K> void baz25(B<K, K> a) {}
  abstract B<? extends T,?> foo25();
  void bar25(A<?> a){
    baz25<error descr="'baz25(B<K,K>)' in 'A' cannot be applied to '(B<capture<? extends capture<?>>,capture<?>>)'">(a.foo25())</error>;
  }


  <K> void baz26(B<K, K> a) {}
  abstract B<? extends T,?> foo26();
  void bar26(A<? super T> a){
    baz26<error descr="'baz26(B<K,K>)' in 'A' cannot be applied to '(B<capture<? extends capture<? super T>>,capture<?>>)'">(a.foo26())</error>;
  }


  <K> void baz27(B<K, K> a) {}
  abstract B<? extends T,?> foo27();
  void bar27(A<? extends T> a){
    baz27<error descr="'baz27(B<K,K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<?>>)'">(a.foo27())</error>;
  }


  <K> void baz28(B<K, K> a) {}
  abstract B<?,?> foo28();
  void bar28(A<?> a){
    baz28<error descr="'baz28(B<K,K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo28())</error>;
  }


  <K> void baz29(B<K, K> a) {}
  abstract B<?,?> foo29();
  void bar29(A<? super T> a){
    baz29<error descr="'baz29(B<K,K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo29())</error>;
  }


  <K> void baz30(B<K, K> a) {}
  abstract B<?,?> foo30();
  void bar30(A<? extends T> a){
    baz30<error descr="'baz30(B<K,K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo30())</error>;
  }


  <K> void baz31(B<K, ? extends K> a) {}
  abstract B<T,T> foo31();
  void bar31(A<?> a){
    baz31<error descr="'baz31(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo31())</error>;
  }


  <K> void baz32(B<K, ? extends K> a) {}
  abstract B<T,T> foo32();
  void bar32(A<? super T> a){
    baz32<error descr="'baz32(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? super T>>)'">(a.foo32())</error>;
  }


  <K> void baz33(B<K, ? extends K> a) {}
  abstract B<T,T> foo33();
  void bar33(A<? extends T> a){
    baz33<error descr="'baz33(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends T>>)'">(a.foo33())</error>;
  }


  <K> void baz34(B<K, ? extends K> a) {}
  abstract B<T,? super T> foo34();
  void bar34(A<?> a){
    baz34<error descr="'baz34(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<?>,capture<? super capture<?>>>)'">(a.foo34())</error>;
  }


  <K> void baz35(B<K, ? extends K> a) {}
  abstract B<T,? super T> foo35();
  void bar35(A<? super T> a){
    baz35<error descr="'baz35(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? super T>>)'">(a.foo35())</error>;
  }


  <K> void baz36(B<K, ? extends K> a) {}
  abstract B<T,? super T> foo36();
  void bar36(A<? extends T> a){
    baz36<error descr="'baz36(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? super capture<? extends T>>>)'">(a.foo36())</error>;
  }


  <K> void baz37(B<K, ? extends K> a) {}
  abstract B<T,? extends T> foo37();
  void bar37(A<?> a){
    baz37<error descr="'baz37(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<?>,capture<? extends capture<?>>>)'">(a.foo37())</error>;
  }


  <K> void baz38(B<K, ? extends K> a) {}
  abstract B<T,? extends T> foo38();
  void bar38(A<? super T> a){
    baz38<error descr="'baz38(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? extends capture<? super T>>>)'">(a.foo38())</error>;
  }


  <K> void baz39(B<K, ? extends K> a) {}
  abstract B<T,? extends T> foo39();
  void bar39(A<? extends T> a){
    baz39<error descr="'baz39(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends T>>)'">(a.foo39())</error>;
  }


  <K> void baz40(B<K, ? extends K> a) {}
  abstract B<T,?> foo40();
  void bar40(A<?> a){
    baz40<error descr="'baz40(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo40())</error>;
  }


  <K> void baz41(B<K, ? extends K> a) {}
  abstract B<T,?> foo41();
  void bar41(A<? super T> a){
    baz41<error descr="'baz41(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<?>>)'">(a.foo41())</error>;
  }


  <K> void baz42(B<K, ? extends K> a) {}
  abstract B<T,?> foo42();
  void bar42(A<? extends T> a){
    baz42<error descr="'baz42(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<?>>)'">(a.foo42())</error>;
  }


  <K> void baz43(B<K, ? extends K> a) {}
  abstract B<? super T,? super T> foo43();
  void bar43(A<?> a){
    baz43<error descr="'baz43(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<? super capture<?>>>)'">(a.foo43())</error>;
  }


  <K> void baz44(B<K, ? extends K> a) {}
  abstract B<? super T,? super T> foo44();
  void bar44(A<? super T> a){
    baz44<error descr="'baz44(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? super T>>)'">(a.foo44())</error>;
  }


  <K> void baz45(B<K, ? extends K> a) {}
  abstract B<? super T,? super T> foo45();
  void bar45(A<? extends T> a){
    baz45<error descr="'baz45(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<? super capture<? extends T>>>)'">(a.foo45())</error>;
  }


  <K> void baz46(B<K, ? extends K> a) {}
  abstract B<? super T,? extends T> foo46();
  void bar46(A<?> a){
    baz46<error descr="'baz46(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<? extends capture<?>>>)'">(a.foo46())</error>;
  }


  <K> void baz47(B<K, ? extends K> a) {}
  abstract B<? super T,? extends T> foo47();
  void bar47(A<? super T> a){
    baz47<error descr="'baz47(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? extends capture<? super T>>>)'">(a.foo47())</error>;
  }


  <K> void baz48(B<K, ? extends K> a) {}
  abstract B<? super T,? extends T> foo48();
  void bar48(A<? extends T> a){
    baz48<error descr="'baz48(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<? extends T>>)'">(a.foo48())</error>;
  }


  <K> void baz49(B<K, ? extends K> a) {}
  abstract B<? super T,?> foo49();
  void bar49(A<?> a){
    baz49<error descr="'baz49(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<?>>)'">(a.foo49())</error>;
  }


  <K> void baz50(B<K, ? extends K> a) {}
  abstract B<? super T,?> foo50();
  void bar50(A<? super T> a){
    baz50<error descr="'baz50(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<?>>)'">(a.foo50())</error>;
  }


  <K> void baz51(B<K, ? extends K> a) {}
  abstract B<? super T,?> foo51();
  void bar51(A<? extends T> a){
    baz51<error descr="'baz51(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<?>>)'">(a.foo51())</error>;
  }


  <K> void baz52(B<K, ? extends K> a) {}
  abstract B<? extends T,? extends T> foo52();
  void bar52(A<?> a){
    baz52<error descr="'baz52(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? extends capture<?>>,capture<? extends capture<?>>>)'">(a.foo52())</error>;
  }


  <K> void baz53(B<K, ? extends K> a) {}
  abstract B<? extends T,? extends T> foo53();
  void bar53(A<? super T> a){
    baz53<error descr="'baz53(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? extends capture<? super T>>,capture<? extends capture<? super T>>>)'">(a.foo53())</error>;
  }


  <K> void baz54(B<K, ? extends K> a) {}
  abstract B<? extends T,? extends T> foo54();
  void bar54(A<? extends T> a){
    baz54<error descr="'baz54(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends T>>)'">(a.foo54())</error>;
  }


  <K> void baz55(B<K, ? extends K> a) {}
  abstract B<? extends T,?> foo55();
  void bar55(A<?> a){
    baz55<error descr="'baz55(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? extends capture<?>>,capture<?>>)'">(a.foo55())</error>;
  }


  <K> void baz56(B<K, ? extends K> a) {}
  abstract B<? extends T,?> foo56();
  void bar56(A<? super T> a){
    baz56<error descr="'baz56(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? extends capture<? super T>>,capture<?>>)'">(a.foo56())</error>;
  }


  <K> void baz57(B<K, ? extends K> a) {}
  abstract B<? extends T,?> foo57();
  void bar57(A<? extends T> a){
    baz57<error descr="'baz57(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<?>>)'">(a.foo57())</error>;
  }


  <K> void baz58(B<K, ? extends K> a) {}
  abstract B<?,?> foo58();
  void bar58(A<?> a){
    baz58<error descr="'baz58(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo58())</error>;
  }


  <K> void baz59(B<K, ? extends K> a) {}
  abstract B<?,?> foo59();
  void bar59(A<? super T> a){
    baz59<error descr="'baz59(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo59())</error>;
  }


  <K> void baz60(B<K, ? extends K> a) {}
  abstract B<?,?> foo60();
  void bar60(A<? extends T> a){
    baz60<error descr="'baz60(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo60())</error>;
  }


  <K> void baz61(B<K, ? super K> a) {}
  abstract B<T,T> foo61();
  void bar61(A<?> a){
    baz61<error descr="'baz61(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo61())</error>;
  }


  <K> void baz62(B<K, ? super K> a) {}
  abstract B<T,T> foo62();
  void bar62(A<? super T> a){
    baz62<error descr="'baz62(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? super T>>)'">(a.foo62())</error>;
  }


  <K> void baz63(B<K, ? super K> a) {}
  abstract B<T,T> foo63();
  void bar63(A<? extends T> a){
    baz63<error descr="'baz63(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends T>>)'">(a.foo63())</error>;
  }


  <K> void baz64(B<K, ? super K> a) {}
  abstract B<T,? super T> foo64();
  void bar64(A<?> a){
    baz64<error descr="'baz64(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<? super capture<?>>>)'">(a.foo64())</error>;
  }


  <K> void baz65(B<K, ? super K> a) {}
  abstract B<T,? super T> foo65();
  void bar65(A<? super T> a){
    baz65<error descr="'baz65(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? super T>>)'">(a.foo65())</error>;
  }


  <K> void baz66(B<K, ? super K> a) {}
  abstract B<T,? super T> foo66();
  void bar66(A<? extends T> a){
    baz66<error descr="'baz66(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? super capture<? extends T>>>)'">(a.foo66())</error>;
  }


  <K> void baz67(B<K, ? super K> a) {}
  abstract B<T,? extends T> foo67();
  void bar67(A<?> a){
    baz67<error descr="'baz67(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<? extends capture<?>>>)'">(a.foo67())</error>;
  }


  <K> void baz68(B<K, ? super K> a) {}
  abstract B<T,? extends T> foo68();
  void bar68(A<? super T> a){
    baz68<error descr="'baz68(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? extends capture<? super T>>>)'">(a.foo68())</error>;
  }


  <K> void baz69(B<K, ? super K> a) {}
  abstract B<T,? extends T> foo69();
  void bar69(A<? extends T> a){
    baz69<error descr="'baz69(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends T>>)'">(a.foo69())</error>;
  }


  <K> void baz70(B<K, ? super K> a) {}
  abstract B<T,?> foo70();
  void bar70(A<?> a){
    baz70<error descr="'baz70(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo70())</error>;
  }


  <K> void baz71(B<K, ? super K> a) {}
  abstract B<T,?> foo71();
  void bar71(A<? super T> a){
    baz71<error descr="'baz71(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<?>>)'">(a.foo71())</error>;
  }


  <K> void baz72(B<K, ? super K> a) {}
  abstract B<T,?> foo72();
  void bar72(A<? extends T> a){
    baz72<error descr="'baz72(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<?>>)'">(a.foo72())</error>;
  }


  <K> void baz73(B<K, ? super K> a) {}
  abstract B<? super T,? super T> foo73();
  void bar73(A<?> a){
    baz73<error descr="'baz73(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<? super capture<?>>>)'">(a.foo73())</error>;
  }


  <K> void baz74(B<K, ? super K> a) {}
  abstract B<? super T,? super T> foo74();
  void bar74(A<? super T> a){
    baz74<error descr="'baz74(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? super T>>)'">(a.foo74())</error>;
  }


  <K> void baz75(B<K, ? super K> a) {}
  abstract B<? super T,? super T> foo75();
  void bar75(A<? extends T> a){
    baz75<error descr="'baz75(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<? super capture<? extends T>>>)'">(a.foo75())</error>;
  }


  <K> void baz76(B<K, ? super K> a) {}
  abstract B<? super T,? extends T> foo76();
  void bar76(A<?> a){
    baz76<error descr="'baz76(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<? extends capture<?>>>)'">(a.foo76())</error>;
  }


  <K> void baz77(B<K, ? super K> a) {}
  abstract B<? super T,? extends T> foo77();
  void bar77(A<? super T> a){
    baz77<error descr="'baz77(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? extends capture<? super T>>>)'">(a.foo77())</error>;
  }


  <K> void baz78(B<K, ? super K> a) {}
  abstract B<? super T,? extends T> foo78();
  void bar78(A<? extends T> a){
    baz78<error descr="'baz78(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<? extends T>>)'">(a.foo78())</error>;
  }


  <K> void baz79(B<K, ? super K> a) {}
  abstract B<? super T,?> foo79();
  void bar79(A<?> a){
    baz79<error descr="'baz79(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<?>>)'">(a.foo79())</error>;
  }


  <K> void baz80(B<K, ? super K> a) {}
  abstract B<? super T,?> foo80();
  void bar80(A<? super T> a){
    baz80<error descr="'baz80(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<?>>)'">(a.foo80())</error>;
  }


  <K> void baz81(B<K, ? super K> a) {}
  abstract B<? super T,?> foo81();
  void bar81(A<? extends T> a){
    baz81<error descr="'baz81(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<?>>)'">(a.foo81())</error>;
  }


  <K> void baz82(B<K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo82();
  void bar82(A<?> a){
    baz82<error descr="'baz82(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends capture<?>>,capture<? extends capture<?>>>)'">(a.foo82())</error>;
  }


  <K> void baz83(B<K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo83();
  void bar83(A<? super T> a){
    baz83<error descr="'baz83(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends capture<? super T>>,capture<? extends capture<? super T>>>)'">(a.foo83())</error>;
  }


  <K> void baz84(B<K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo84();
  void bar84(A<? extends T> a){
    baz84<error descr="'baz84(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends T>>)'">(a.foo84())</error>;
  }


  <K> void baz85(B<K, ? super K> a) {}
  abstract B<? extends T,?> foo85();
  void bar85(A<?> a){
    baz85<error descr="'baz85(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends capture<?>>,capture<?>>)'">(a.foo85())</error>;
  }


  <K> void baz86(B<K, ? super K> a) {}
  abstract B<? extends T,?> foo86();
  void bar86(A<? super T> a){
    baz86<error descr="'baz86(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends capture<? super T>>,capture<?>>)'">(a.foo86())</error>;
  }


  <K> void baz87(B<K, ? super K> a) {}
  abstract B<? extends T,?> foo87();
  void bar87(A<? extends T> a){
    baz87<error descr="'baz87(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<?>>)'">(a.foo87())</error>;
  }


  <K> void baz88(B<K, ? super K> a) {}
  abstract B<?,?> foo88();
  void bar88(A<?> a){
    baz88<error descr="'baz88(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo88())</error>;
  }


  <K> void baz89(B<K, ? super K> a) {}
  abstract B<?,?> foo89();
  void bar89(A<? super T> a){
    baz89<error descr="'baz89(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo89())</error>;
  }


  <K> void baz90(B<K, ? super K> a) {}
  abstract B<?,?> foo90();
  void bar90(A<? extends T> a){
    baz90<error descr="'baz90(B<K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo90())</error>;
  }


  <K> void baz91(B<K, ?> a) {}
  abstract B<T,T> foo91();
  void bar91(A<?> a){
    baz91(a.foo91());
  }


  <K> void baz92(B<K, ?> a) {}
  abstract B<T,T> foo92();
  void bar92(A<? super T> a){
    baz92(a.foo92());
  }


  <K> void baz93(B<K, ?> a) {}
  abstract B<T,T> foo93();
  void bar93(A<? extends T> a){
    baz93(a.foo93());
  }


  <K> void baz94(B<K, ?> a) {}
  abstract B<T,? super T> foo94();
  void bar94(A<?> a){
    baz94(a.foo94());
  }


  <K> void baz95(B<K, ?> a) {}
  abstract B<T,? super T> foo95();
  void bar95(A<? super T> a){
    baz95(a.foo95());
  }


  <K> void baz96(B<K, ?> a) {}
  abstract B<T,? super T> foo96();
  void bar96(A<? extends T> a){
    baz96(a.foo96());
  }


  <K> void baz97(B<K, ?> a) {}
  abstract B<T,? extends T> foo97();
  void bar97(A<?> a){
    baz97(a.foo97());
  }


  <K> void baz98(B<K, ?> a) {}
  abstract B<T,? extends T> foo98();
  void bar98(A<? super T> a){
    baz98(a.foo98());
  }


  <K> void baz99(B<K, ?> a) {}
  abstract B<T,? extends T> foo99();
  void bar99(A<? extends T> a){
    baz99(a.foo99());
  }


  <K> void baz100(B<K, ?> a) {}
  abstract B<T,?> foo100();
  void bar100(A<?> a){
    baz100(a.foo100());
  }


  <K> void baz101(B<K, ?> a) {}
  abstract B<T,?> foo101();
  void bar101(A<? super T> a){
    baz101(a.foo101());
  }


  <K> void baz102(B<K, ?> a) {}
  abstract B<T,?> foo102();
  void bar102(A<? extends T> a){
    baz102(a.foo102());
  }


  <K> void baz103(B<K, ?> a) {}
  abstract B<? super T,? super T> foo103();
  void bar103(A<?> a){
    baz103(a.foo103());
  }


  <K> void baz104(B<K, ?> a) {}
  abstract B<? super T,? super T> foo104();
  void bar104(A<? super T> a){
    baz104(a.foo104());
  }


  <K> void baz105(B<K, ?> a) {}
  abstract B<? super T,? super T> foo105();
  void bar105(A<? extends T> a){
    baz105(a.foo105());
  }


  <K> void baz106(B<K, ?> a) {}
  abstract B<? super T,? extends T> foo106();
  void bar106(A<?> a){
    baz106(a.foo106());
  }


  <K> void baz107(B<K, ?> a) {}
  abstract B<? super T,? extends T> foo107();
  void bar107(A<? super T> a){
    baz107(a.foo107());
  }


  <K> void baz108(B<K, ?> a) {}
  abstract B<? super T,? extends T> foo108();
  void bar108(A<? extends T> a){
    baz108(a.foo108());
  }


  <K> void baz109(B<K, ?> a) {}
  abstract B<? super T,?> foo109();
  void bar109(A<?> a){
    baz109(a.foo109());
  }


  <K> void baz110(B<K, ?> a) {}
  abstract B<? super T,?> foo110();
  void bar110(A<? super T> a){
    baz110(a.foo110());
  }


  <K> void baz111(B<K, ?> a) {}
  abstract B<? super T,?> foo111();
  void bar111(A<? extends T> a){
    baz111(a.foo111());
  }


  <K> void baz112(B<K, ?> a) {}
  abstract B<? extends T,? extends T> foo112();
  void bar112(A<?> a){
    baz112(a.foo112());
  }


  <K> void baz113(B<K, ?> a) {}
  abstract B<? extends T,? extends T> foo113();
  void bar113(A<? super T> a){
    baz113(a.foo113());
  }


  <K> void baz114(B<K, ?> a) {}
  abstract B<? extends T,? extends T> foo114();
  void bar114(A<? extends T> a){
    baz114(a.foo114());
  }


  <K> void baz115(B<K, ?> a) {}
  abstract B<? extends T,?> foo115();
  void bar115(A<?> a){
    baz115(a.foo115());
  }


  <K> void baz116(B<K, ?> a) {}
  abstract B<? extends T,?> foo116();
  void bar116(A<? super T> a){
    baz116(a.foo116());
  }


  <K> void baz117(B<K, ?> a) {}
  abstract B<? extends T,?> foo117();
  void bar117(A<? extends T> a){
    baz117(a.foo117());
  }


  <K> void baz118(B<K, ?> a) {}
  abstract B<?,?> foo118();
  void bar118(A<?> a){
    baz118(a.foo118());
  }


  <K> void baz119(B<K, ?> a) {}
  abstract B<?,?> foo119();
  void bar119(A<? super T> a){
    baz119(a.foo119());
  }


  <K> void baz120(B<K, ?> a) {}
  abstract B<?,?> foo120();
  void bar120(A<? extends T> a){
    baz120(a.foo120());
  }


  <K> void baz121(B<? extends K, ? extends K> a) {}
  abstract B<T,T> foo121();
  void bar121(A<?> a){
    baz121(a.foo121());
  }


  <K> void baz122(B<? extends K, ? extends K> a) {}
  abstract B<T,T> foo122();
  void bar122(A<? super T> a){
    baz122(a.foo122());
  }


  <K> void baz123(B<? extends K, ? extends K> a) {}
  abstract B<T,T> foo123();
  void bar123(A<? extends T> a){
    baz123(a.foo123());
  }


  <K> void baz124(B<? extends K, ? extends K> a) {}
  abstract B<T,? super T> foo124();
  void bar124(A<?> a){
    baz124(a.foo124());
  }


  <K> void baz125(B<? extends K, ? extends K> a) {}
  abstract B<T,? super T> foo125();
  void bar125(A<? super T> a){
    baz125(a.foo125());
  }


  <K> void baz126(B<? extends K, ? extends K> a) {}
  abstract B<T,? super T> foo126();
  void bar126(A<? extends T> a){
    baz126(a.foo126());
  }


  <K> void baz127(B<? extends K, ? extends K> a) {}
  abstract B<T,? extends T> foo127();
  void bar127(A<?> a){
    baz127(a.foo127());
  }


  <K> void baz128(B<? extends K, ? extends K> a) {}
  abstract B<T,? extends T> foo128();
  void bar128(A<? super T> a){
    baz128(a.foo128());
  }


  <K> void baz129(B<? extends K, ? extends K> a) {}
  abstract B<T,? extends T> foo129();
  void bar129(A<? extends T> a){
    baz129(a.foo129());
  }


  <K> void baz130(B<? extends K, ? extends K> a) {}
  abstract B<T,?> foo130();
  void bar130(A<?> a){
    baz130(a.foo130());
  }


  <K> void baz131(B<? extends K, ? extends K> a) {}
  abstract B<T,?> foo131();
  void bar131(A<? super T> a){
    baz131(a.foo131());
  }


  <K> void baz132(B<? extends K, ? extends K> a) {}
  abstract B<T,?> foo132();
  void bar132(A<? extends T> a){
    baz132(a.foo132());
  }


  <K> void baz133(B<? extends K, ? extends K> a) {}
  abstract B<? super T,? super T> foo133();
  void bar133(A<?> a){
    baz133(a.foo133());
  }


  <K> void baz134(B<? extends K, ? extends K> a) {}
  abstract B<? super T,? super T> foo134();
  void bar134(A<? super T> a){
    baz134(a.foo134());
  }


  <K> void baz135(B<? extends K, ? extends K> a) {}
  abstract B<? super T,? super T> foo135();
  void bar135(A<? extends T> a){
    baz135(a.foo135());
  }


  <K> void baz136(B<? extends K, ? extends K> a) {}
  abstract B<? super T,? extends T> foo136();
  void bar136(A<?> a){
    baz136(a.foo136());
  }


  <K> void baz137(B<? extends K, ? extends K> a) {}
  abstract B<? super T,? extends T> foo137();
  void bar137(A<? super T> a){
    baz137(a.foo137());
  }


  <K> void baz138(B<? extends K, ? extends K> a) {}
  abstract B<? super T,? extends T> foo138();
  void bar138(A<? extends T> a){
    baz138(a.foo138());
  }


  <K> void baz139(B<? extends K, ? extends K> a) {}
  abstract B<? super T,?> foo139();
  void bar139(A<?> a){
    baz139(a.foo139());
  }


  <K> void baz140(B<? extends K, ? extends K> a) {}
  abstract B<? super T,?> foo140();
  void bar140(A<? super T> a){
    baz140(a.foo140());
  }


  <K> void baz141(B<? extends K, ? extends K> a) {}
  abstract B<? super T,?> foo141();
  void bar141(A<? extends T> a){
    baz141(a.foo141());
  }


  <K> void baz142(B<? extends K, ? extends K> a) {}
  abstract B<? extends T,? extends T> foo142();
  void bar142(A<?> a){
    baz142(a.foo142());
  }


  <K> void baz143(B<? extends K, ? extends K> a) {}
  abstract B<? extends T,? extends T> foo143();
  void bar143(A<? super T> a){
    baz143(a.foo143());
  }


  <K> void baz144(B<? extends K, ? extends K> a) {}
  abstract B<? extends T,? extends T> foo144();
  void bar144(A<? extends T> a){
    baz144(a.foo144());
  }


  <K> void baz145(B<? extends K, ? extends K> a) {}
  abstract B<? extends T,?> foo145();
  void bar145(A<?> a){
    baz145(a.foo145());
  }


  <K> void baz146(B<? extends K, ? extends K> a) {}
  abstract B<? extends T,?> foo146();
  void bar146(A<? super T> a){
    baz146(a.foo146());
  }


  <K> void baz147(B<? extends K, ? extends K> a) {}
  abstract B<? extends T,?> foo147();
  void bar147(A<? extends T> a){
    baz147(a.foo147());
  }


  <K> void baz148(B<? extends K, ? extends K> a) {}
  abstract B<?,?> foo148();
  void bar148(A<?> a){
    baz148(a.foo148());
  }


  <K> void baz149(B<? extends K, ? extends K> a) {}
  abstract B<?,?> foo149();
  void bar149(A<? super T> a){
    baz149(a.foo149());
  }


  <K> void baz150(B<? extends K, ? extends K> a) {}
  abstract B<?,?> foo150();
  void bar150(A<? extends T> a){
    baz150(a.foo150());
  }


  <K> void baz151(B<? extends K, ? super K> a) {}
  abstract B<T,T> foo151();
  void bar151(A<?> a){
    baz151<error descr="'baz151(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo151())</error>;
  }


  <K> void baz152(B<? extends K, ? super K> a) {}
  abstract B<T,T> foo152();
  void bar152(A<? super T> a){
    baz152<error descr="'baz152(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? super T>>)'">(a.foo152())</error>;
  }


  <K> void baz153(B<? extends K, ? super K> a) {}
  abstract B<T,T> foo153();
  void bar153(A<? extends T> a){
    baz153<error descr="'baz153(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends T>>)'">(a.foo153())</error>;
  }


  <K> void baz154(B<? extends K, ? super K> a) {}
  abstract B<T,? super T> foo154();
  void bar154(A<?> a){
    baz154<error descr="'baz154(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<? super capture<?>>>)'">(a.foo154())</error>;
  }


  <K> void baz155(B<? extends K, ? super K> a) {}
  abstract B<T,? super T> foo155();
  void bar155(A<? super T> a){
    baz155<error descr="'baz155(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? super T>>)'">(a.foo155())</error>;
  }


  <K> void baz156(B<? extends K, ? super K> a) {}
  abstract B<T,? super T> foo156();
  void bar156(A<? extends T> a){
    baz156<error descr="'baz156(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? super capture<? extends T>>>)'">(a.foo156())</error>;
  }


  <K> void baz157(B<? extends K, ? super K> a) {}
  abstract B<T,? extends T> foo157();
  void bar157(A<?> a){
    baz157<error descr="'baz157(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<? extends capture<?>>>)'">(a.foo157())</error>;
  }


  <K> void baz158(B<? extends K, ? super K> a) {}
  abstract B<T,? extends T> foo158();
  void bar158(A<? super T> a){
    baz158<error descr="'baz158(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? extends capture<? super T>>>)'">(a.foo158())</error>;
  }


  <K> void baz159(B<? extends K, ? super K> a) {}
  abstract B<T,? extends T> foo159();
  void bar159(A<? extends T> a){
    baz159<error descr="'baz159(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends T>>)'">(a.foo159())</error>;
  }


  <K> void baz160(B<? extends K, ? super K> a) {}
  abstract B<T,?> foo160();
  void bar160(A<?> a){
    baz160<error descr="'baz160(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo160())</error>;
  }


  <K> void baz161(B<? extends K, ? super K> a) {}
  abstract B<T,?> foo161();
  void bar161(A<? super T> a){
    baz161<error descr="'baz161(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<?>>)'">(a.foo161())</error>;
  }


  <K> void baz162(B<? extends K, ? super K> a) {}
  abstract B<T,?> foo162();
  void bar162(A<? extends T> a){
    baz162<error descr="'baz162(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<?>>)'">(a.foo162())</error>;
  }


  <K> void baz163(B<? extends K, ? super K> a) {}
  abstract B<? super T,? super T> foo163();
  void bar163(A<?> a){
    baz163<error descr="'baz163(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<? super capture<?>>>)'">(a.foo163())</error>;
  }


  <K> void baz164(B<? extends K, ? super K> a) {}
  abstract B<? super T,? super T> foo164();
  void bar164(A<? super T> a){
    baz164<error descr="'baz164(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? super T>>)'">(a.foo164())</error>;
  }


  <K> void baz165(B<? extends K, ? super K> a) {}
  abstract B<? super T,? super T> foo165();
  void bar165(A<? extends T> a){
    baz165<error descr="'baz165(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<? super capture<? extends T>>>)'">(a.foo165())</error>;
  }


  <K> void baz166(B<? extends K, ? super K> a) {}
  abstract B<? super T,? extends T> foo166();
  void bar166(A<?> a){
    baz166<error descr="'baz166(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<? extends capture<?>>>)'">(a.foo166())</error>;
  }


  <K> void baz167(B<? extends K, ? super K> a) {}
  abstract B<? super T,? extends T> foo167();
  void bar167(A<? super T> a){
    baz167<error descr="'baz167(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? extends capture<? super T>>>)'">(a.foo167())</error>;
  }


  <K> void baz168(B<? extends K, ? super K> a) {}
  abstract B<? super T,? extends T> foo168();
  void bar168(A<? extends T> a){
    baz168<error descr="'baz168(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<? extends T>>)'">(a.foo168())</error>;
  }


  <K> void baz169(B<? extends K, ? super K> a) {}
  abstract B<? super T,?> foo169();
  void bar169(A<?> a){
    baz169<error descr="'baz169(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<?>>)'">(a.foo169())</error>;
  }


  <K> void baz170(B<? extends K, ? super K> a) {}
  abstract B<? super T,?> foo170();
  void bar170(A<? super T> a){
    baz170<error descr="'baz170(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<?>>)'">(a.foo170())</error>;
  }


  <K> void baz171(B<? extends K, ? super K> a) {}
  abstract B<? super T,?> foo171();
  void bar171(A<? extends T> a){
    baz171<error descr="'baz171(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<?>>)'">(a.foo171())</error>;
  }


  <K> void baz172(B<? extends K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo172();
  void bar172(A<?> a){
    baz172<error descr="'baz172(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends capture<?>>,capture<? extends capture<?>>>)'">(a.foo172())</error>;
  }


  <K> void baz173(B<? extends K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo173();
  void bar173(A<? super T> a){
    baz173<error descr="'baz173(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends capture<? super T>>,capture<? extends capture<? super T>>>)'">(a.foo173())</error>;
  }


  <K> void baz174(B<? extends K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo174();
  void bar174(A<? extends T> a){
    baz174<error descr="'baz174(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends T>>)'">(a.foo174())</error>;
  }


  <K> void baz175(B<? extends K, ? super K> a) {}
  abstract B<? extends T,?> foo175();
  void bar175(A<?> a){
    baz175<error descr="'baz175(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends capture<?>>,capture<?>>)'">(a.foo175())</error>;
  }


  <K> void baz176(B<? extends K, ? super K> a) {}
  abstract B<? extends T,?> foo176();
  void bar176(A<? super T> a){
    baz176<error descr="'baz176(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends capture<? super T>>,capture<?>>)'">(a.foo176())</error>;
  }


  <K> void baz177(B<? extends K, ? super K> a) {}
  abstract B<? extends T,?> foo177();
  void bar177(A<? extends T> a){
    baz177<error descr="'baz177(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<?>>)'">(a.foo177())</error>;
  }


  <K> void baz178(B<? extends K, ? super K> a) {}
  abstract B<?,?> foo178();
  void bar178(A<?> a){
    baz178<error descr="'baz178(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo178())</error>;
  }


  <K> void baz179(B<? extends K, ? super K> a) {}
  abstract B<?,?> foo179();
  void bar179(A<? super T> a){
    baz179<error descr="'baz179(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo179())</error>;
  }


  <K> void baz180(B<? extends K, ? super K> a) {}
  abstract B<?,?> foo180();
  void bar180(A<? extends T> a){
    baz180<error descr="'baz180(B<? extends K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo180())</error>;
  }


  <K> void baz181(B<? extends K, ?> a) {}
  abstract B<T,T> foo181();
  void bar181(A<?> a){
    baz181(a.foo181());
  }


  <K> void baz182(B<? extends K, ?> a) {}
  abstract B<T,T> foo182();
  void bar182(A<? super T> a){
    baz182(a.foo182());
  }


  <K> void baz183(B<? extends K, ?> a) {}
  abstract B<T,T> foo183();
  void bar183(A<? extends T> a){
    baz183(a.foo183());
  }


  <K> void baz184(B<? extends K, ?> a) {}
  abstract B<T,? super T> foo184();
  void bar184(A<?> a){
    baz184(a.foo184());
  }


  <K> void baz185(B<? extends K, ?> a) {}
  abstract B<T,? super T> foo185();
  void bar185(A<? super T> a){
    baz185(a.foo185());
  }


  <K> void baz186(B<? extends K, ?> a) {}
  abstract B<T,? super T> foo186();
  void bar186(A<? extends T> a){
    baz186(a.foo186());
  }


  <K> void baz187(B<? extends K, ?> a) {}
  abstract B<T,? extends T> foo187();
  void bar187(A<?> a){
    baz187(a.foo187());
  }


  <K> void baz188(B<? extends K, ?> a) {}
  abstract B<T,? extends T> foo188();
  void bar188(A<? super T> a){
    baz188(a.foo188());
  }


  <K> void baz189(B<? extends K, ?> a) {}
  abstract B<T,? extends T> foo189();
  void bar189(A<? extends T> a){
    baz189(a.foo189());
  }


  <K> void baz190(B<? extends K, ?> a) {}
  abstract B<T,?> foo190();
  void bar190(A<?> a){
    baz190(a.foo190());
  }


  <K> void baz191(B<? extends K, ?> a) {}
  abstract B<T,?> foo191();
  void bar191(A<? super T> a){
    baz191(a.foo191());
  }


  <K> void baz192(B<? extends K, ?> a) {}
  abstract B<T,?> foo192();
  void bar192(A<? extends T> a){
    baz192(a.foo192());
  }


  <K> void baz193(B<? extends K, ?> a) {}
  abstract B<? super T,? super T> foo193();
  void bar193(A<?> a){
    baz193(a.foo193());
  }


  <K> void baz194(B<? extends K, ?> a) {}
  abstract B<? super T,? super T> foo194();
  void bar194(A<? super T> a){
    baz194(a.foo194());
  }


  <K> void baz195(B<? extends K, ?> a) {}
  abstract B<? super T,? super T> foo195();
  void bar195(A<? extends T> a){
    baz195(a.foo195());
  }


  <K> void baz196(B<? extends K, ?> a) {}
  abstract B<? super T,? extends T> foo196();
  void bar196(A<?> a){
    baz196(a.foo196());
  }


  <K> void baz197(B<? extends K, ?> a) {}
  abstract B<? super T,? extends T> foo197();
  void bar197(A<? super T> a){
    baz197(a.foo197());
  }


  <K> void baz198(B<? extends K, ?> a) {}
  abstract B<? super T,? extends T> foo198();
  void bar198(A<? extends T> a){
    baz198(a.foo198());
  }


  <K> void baz199(B<? extends K, ?> a) {}
  abstract B<? super T,?> foo199();
  void bar199(A<?> a){
    baz199(a.foo199());
  }


  <K> void baz200(B<? extends K, ?> a) {}
  abstract B<? super T,?> foo200();
  void bar200(A<? super T> a){
    baz200(a.foo200());
  }


  <K> void baz201(B<? extends K, ?> a) {}
  abstract B<? super T,?> foo201();
  void bar201(A<? extends T> a){
    baz201(a.foo201());
  }


  <K> void baz202(B<? extends K, ?> a) {}
  abstract B<? extends T,? extends T> foo202();
  void bar202(A<?> a){
    baz202(a.foo202());
  }


  <K> void baz203(B<? extends K, ?> a) {}
  abstract B<? extends T,? extends T> foo203();
  void bar203(A<? super T> a){
    baz203(a.foo203());
  }


  <K> void baz204(B<? extends K, ?> a) {}
  abstract B<? extends T,? extends T> foo204();
  void bar204(A<? extends T> a){
    baz204(a.foo204());
  }


  <K> void baz205(B<? extends K, ?> a) {}
  abstract B<? extends T,?> foo205();
  void bar205(A<?> a){
    baz205(a.foo205());
  }


  <K> void baz206(B<? extends K, ?> a) {}
  abstract B<? extends T,?> foo206();
  void bar206(A<? super T> a){
    baz206(a.foo206());
  }


  <K> void baz207(B<? extends K, ?> a) {}
  abstract B<? extends T,?> foo207();
  void bar207(A<? extends T> a){
    baz207(a.foo207());
  }


  <K> void baz208(B<? extends K, ?> a) {}
  abstract B<?,?> foo208();
  void bar208(A<?> a){
    baz208(a.foo208());
  }


  <K> void baz209(B<? extends K, ?> a) {}
  abstract B<?,?> foo209();
  void bar209(A<? super T> a){
    baz209(a.foo209());
  }


  <K> void baz210(B<? extends K, ?> a) {}
  abstract B<?,?> foo210();
  void bar210(A<? extends T> a){
    baz210(a.foo210());
  }


  <K> void baz211(B<? super K, ? super K> a) {}
  abstract B<T,T> foo211();
  void bar211(A<?> a){
    baz211<error descr="'baz211(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo211())</error>;
  }


  <K> void baz212(B<? super K, ? super K> a) {}
  abstract B<T,T> foo212();
  void bar212(A<? super T> a){
    baz212(a.foo212());
  }


  <K> void baz213(B<? super K, ? super K> a) {}
  abstract B<T,T> foo213();
  void bar213(A<? extends T> a){
    baz213<error descr="'baz213(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends T>>)'">(a.foo213())</error>;
  }


  <K> void baz214(B<? super K, ? super K> a) {}
  abstract B<T,? super T> foo214();
  void bar214(A<?> a){
    baz214<error descr="'baz214(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<? super capture<?>>>)'">(a.foo214())</error>;
  }


  <K> void baz215(B<? super K, ? super K> a) {}
  abstract B<T,? super T> foo215();
  void bar215(A<? super T> a){
    baz215(a.foo215());
  }


  <K> void baz216(B<? super K, ? super K> a) {}
  abstract B<T,? super T> foo216();
  void bar216(A<? extends T> a){
    baz216<error descr="'baz216(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? super capture<? extends T>>>)'">(a.foo216())</error>;
  }


  <K> void baz217(B<? super K, ? super K> a) {}
  abstract B<T,? extends T> foo217();
  void bar217(A<?> a){
    baz217<error descr="'baz217(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<? extends capture<?>>>)'">(a.foo217())</error>;
  }


  <K> void baz218(B<? super K, ? super K> a) {}
  abstract B<T,? extends T> foo218();
  void bar218(A<? super T> a){
    baz218<error descr="'baz218(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? extends capture<? super T>>>)'">(a.foo218())</error>;
  }


  <K> void baz219(B<? super K, ? super K> a) {}
  abstract B<T,? extends T> foo219();
  void bar219(A<? extends T> a){
    baz219<error descr="'baz219(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends T>>)'">(a.foo219())</error>;
  }


  <K> void baz220(B<? super K, ? super K> a) {}
  abstract B<T,?> foo220();
  void bar220(A<?> a){
    baz220<error descr="'baz220(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo220())</error>;
  }


  <K> void baz221(B<? super K, ? super K> a) {}
  abstract B<T,?> foo221();
  void bar221(A<? super T> a){
    baz221<error descr="'baz221(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<?>>)'">(a.foo221())</error>;
  }


  <K> void baz222(B<? super K, ? super K> a) {}
  abstract B<T,?> foo222();
  void bar222(A<? extends T> a){
    baz222<error descr="'baz222(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<?>>)'">(a.foo222())</error>;
  }


  <K> void baz223(B<? super K, ? super K> a) {}
  abstract B<? super T,? super T> foo223();
  void bar223(A<?> a){
    baz223<error descr="'baz223(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<? super capture<?>>>)'">(a.foo223())</error>;
  }


  <K> void baz224(B<? super K, ? super K> a) {}
  abstract B<? super T,? super T> foo224();
  void bar224(A<? super T> a){
    baz224(a.foo224());
  }


  <K> void baz225(B<? super K, ? super K> a) {}
  abstract B<? super T,? super T> foo225();
  void bar225(A<? extends T> a){
    baz225<error descr="'baz225(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<? super capture<? extends T>>>)'">(a.foo225())</error>;
  }


  <K> void baz226(B<? super K, ? super K> a) {}
  abstract B<? super T,? extends T> foo226();
  void bar226(A<?> a){
    baz226<error descr="'baz226(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<? extends capture<?>>>)'">(a.foo226())</error>;
  }


  <K> void baz227(B<? super K, ? super K> a) {}
  abstract B<? super T,? extends T> foo227();
  void bar227(A<? super T> a){
    baz227<error descr="'baz227(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? extends capture<? super T>>>)'">(a.foo227())</error>;
  }


  <K> void baz228(B<? super K, ? super K> a) {}
  abstract B<? super T,? extends T> foo228();
  void bar228(A<? extends T> a){
    baz228<error descr="'baz228(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<? extends T>>)'">(a.foo228())</error>;
  }


  <K> void baz229(B<? super K, ? super K> a) {}
  abstract B<? super T,?> foo229();
  void bar229(A<?> a){
    baz229<error descr="'baz229(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<?>>)'">(a.foo229())</error>;
  }


  <K> void baz230(B<? super K, ? super K> a) {}
  abstract B<? super T,?> foo230();
  void bar230(A<? super T> a){
    baz230<error descr="'baz230(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<?>>)'">(a.foo230())</error>;
  }


  <K> void baz231(B<? super K, ? super K> a) {}
  abstract B<? super T,?> foo231();
  void bar231(A<? extends T> a){
    baz231<error descr="'baz231(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<?>>)'">(a.foo231())</error>;
  }


  <K> void baz232(B<? super K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo232();
  void bar232(A<?> a){
    baz232<error descr="'baz232(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends capture<?>>,capture<? extends capture<?>>>)'">(a.foo232())</error>;
  }


  <K> void baz233(B<? super K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo233();
  void bar233(A<? super T> a){
    baz233<error descr="'baz233(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends capture<? super T>>,capture<? extends capture<? super T>>>)'">(a.foo233())</error>;
  }


  <K> void baz234(B<? super K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo234();
  void bar234(A<? extends T> a){
    baz234<error descr="'baz234(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends T>>)'">(a.foo234())</error>;
  }


  <K> void baz235(B<? super K, ? super K> a) {}
  abstract B<? extends T,?> foo235();
  void bar235(A<?> a){
    baz235<error descr="'baz235(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends capture<?>>,capture<?>>)'">(a.foo235())</error>;
  }


  <K> void baz236(B<? super K, ? super K> a) {}
  abstract B<? extends T,?> foo236();
  void bar236(A<? super T> a){
    baz236<error descr="'baz236(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends capture<? super T>>,capture<?>>)'">(a.foo236())</error>;
  }


  <K> void baz237(B<? super K, ? super K> a) {}
  abstract B<? extends T,?> foo237();
  void bar237(A<? extends T> a){
    baz237<error descr="'baz237(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<?>>)'">(a.foo237())</error>;
  }


  <K> void baz238(B<? super K, ? super K> a) {}
  abstract B<?,?> foo238();
  void bar238(A<?> a){
    baz238<error descr="'baz238(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo238())</error>;
  }


  <K> void baz239(B<? super K, ? super K> a) {}
  abstract B<?,?> foo239();
  void bar239(A<? super T> a){
    baz239<error descr="'baz239(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo239())</error>;
  }


  <K> void baz240(B<? super K, ? super K> a) {}
  abstract B<?,?> foo240();
  void bar240(A<? extends T> a){
    baz240<error descr="'baz240(B<? super K,? super K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo240())</error>;
  }


  <K> void baz241(B<? super K, ?> a) {}
  abstract B<T,T> foo241();
  void bar241(A<?> a){
    baz241<error descr="'baz241(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo241())</error>;
  }


  <K> void baz242(B<? super K, ?> a) {}
  abstract B<T,T> foo242();
  void bar242(A<? super T> a){
    baz242(a.foo242());
  }


  <K> void baz243(B<? super K, ?> a) {}
  abstract B<T,T> foo243();
  void bar243(A<? extends T> a){
    baz243<error descr="'baz243(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends T>>)'">(a.foo243())</error>;
  }


  <K> void baz244(B<? super K, ?> a) {}
  abstract B<T,? super T> foo244();
  void bar244(A<?> a){
    baz244<error descr="'baz244(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<?>,capture<? super capture<?>>>)'">(a.foo244())</error>;
  }


  <K> void baz245(B<? super K, ?> a) {}
  abstract B<T,? super T> foo245();
  void bar245(A<? super T> a){
    baz245(a.foo245());
  }


  <K> void baz246(B<? super K, ?> a) {}
  abstract B<T,? super T> foo246();
  void bar246(A<? extends T> a){
    baz246<error descr="'baz246(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? super capture<? extends T>>>)'">(a.foo246())</error>;
  }


  <K> void baz247(B<? super K, ?> a) {}
  abstract B<T,? extends T> foo247();
  void bar247(A<?> a){
    baz247<error descr="'baz247(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<?>,capture<? extends capture<?>>>)'">(a.foo247())</error>;
  }


  <K> void baz248(B<? super K, ?> a) {}
  abstract B<T,? extends T> foo248();
  void bar248(A<? super T> a){
    baz248(a.foo248());
  }


  <K> void baz249(B<? super K, ?> a) {}
  abstract B<T,? extends T> foo249();
  void bar249(A<? extends T> a){
    baz249<error descr="'baz249(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends T>>)'">(a.foo249())</error>;
  }


  <K> void baz250(B<? super K, ?> a) {}
  abstract B<T,?> foo250();
  void bar250(A<?> a){
    baz250<error descr="'baz250(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo250())</error>;
  }


  <K> void baz251(B<? super K, ?> a) {}
  abstract B<T,?> foo251();
  void bar251(A<? super T> a){
    baz251(a.foo251());
  }


  <K> void baz252(B<? super K, ?> a) {}
  abstract B<T,?> foo252();
  void bar252(A<? extends T> a){
    baz252<error descr="'baz252(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<?>>)'">(a.foo252())</error>;
  }


  <K> void baz253(B<? super K, ?> a) {}
  abstract B<? super T,? super T> foo253();
  void bar253(A<?> a){
    baz253<error descr="'baz253(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<? super capture<?>>>)'">(a.foo253())</error>;
  }


  <K> void baz254(B<? super K, ?> a) {}
  abstract B<? super T,? super T> foo254();
  void bar254(A<? super T> a){
    baz254(a.foo254());
  }


  <K> void baz255(B<? super K, ?> a) {}
  abstract B<? super T,? super T> foo255();
  void bar255(A<? extends T> a){
    baz255<error descr="'baz255(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<? super capture<? extends T>>>)'">(a.foo255())</error>;
  }


  <K> void baz256(B<? super K, ?> a) {}
  abstract B<? super T,? extends T> foo256();
  void bar256(A<?> a){
    baz256<error descr="'baz256(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<? extends capture<?>>>)'">(a.foo256())</error>;
  }


  <K> void baz257(B<? super K, ?> a) {}
  abstract B<? super T,? extends T> foo257();
  void bar257(A<? super T> a){
    baz257(a.foo257());
  }


  <K> void baz258(B<? super K, ?> a) {}
  abstract B<? super T,? extends T> foo258();
  void bar258(A<? extends T> a){
    baz258<error descr="'baz258(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<? extends T>>)'">(a.foo258())</error>;
  }


  <K> void baz259(B<? super K, ?> a) {}
  abstract B<? super T,?> foo259();
  void bar259(A<?> a){
    baz259<error descr="'baz259(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<?>>)'">(a.foo259())</error>;
  }


  <K> void baz260(B<? super K, ?> a) {}
  abstract B<? super T,?> foo260();
  void bar260(A<? super T> a){
    baz260(a.foo260());
  }


  <K> void baz261(B<? super K, ?> a) {}
  abstract B<? super T,?> foo261();
  void bar261(A<? extends T> a){
    baz261<error descr="'baz261(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<?>>)'">(a.foo261())</error>;
  }


  <K> void baz262(B<? super K, ?> a) {}
  abstract B<? extends T,? extends T> foo262();
  void bar262(A<?> a){
    baz262<error descr="'baz262(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<? extends capture<?>>,capture<? extends capture<?>>>)'">(a.foo262())</error>;
  }


  <K> void baz263(B<? super K, ?> a) {}
  abstract B<? extends T,? extends T> foo263();
  void bar263(A<? super T> a){
    baz263<error descr="'baz263(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<? extends capture<? super T>>,capture<? extends capture<? super T>>>)'">(a.foo263())</error>;
  }


  <K> void baz264(B<? super K, ?> a) {}
  abstract B<? extends T,? extends T> foo264();
  void bar264(A<? extends T> a){
    baz264<error descr="'baz264(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends T>>)'">(a.foo264())</error>;
  }


  <K> void baz265(B<? super K, ?> a) {}
  abstract B<? extends T,?> foo265();
  void bar265(A<?> a){
    baz265<error descr="'baz265(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<? extends capture<?>>,capture<?>>)'">(a.foo265())</error>;
  }


  <K> void baz266(B<? super K, ?> a) {}
  abstract B<? extends T,?> foo266();
  void bar266(A<? super T> a){
    baz266<error descr="'baz266(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<? extends capture<? super T>>,capture<?>>)'">(a.foo266())</error>;
  }


  <K> void baz267(B<? super K, ?> a) {}
  abstract B<? extends T,?> foo267();
  void bar267(A<? extends T> a){
    baz267<error descr="'baz267(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<?>>)'">(a.foo267())</error>;
  }


  <K> void baz268(B<? super K, ?> a) {}
  abstract B<?,?> foo268();
  void bar268(A<?> a){
    baz268<error descr="'baz268(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo268())</error>;
  }


  <K> void baz269(B<? super K, ?> a) {}
  abstract B<?,?> foo269();
  void bar269(A<? super T> a){
    baz269<error descr="'baz269(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo269())</error>;
  }


  <K> void baz270(B<? super K, ?> a) {}
  abstract B<?,?> foo270();
  void bar270(A<? extends T> a){
    baz270<error descr="'baz270(B<? super K,?>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo270())</error>;
  }


  <K> void baz271(B<?, ?> a) {}
  abstract B<T,T> foo271();
  void bar271(A<?> a){
    baz271(a.foo271());
  }


  <K> void baz272(B<?, ?> a) {}
  abstract B<T,T> foo272();
  void bar272(A<? super T> a){
    baz272(a.foo272());
  }


  <K> void baz273(B<?, ?> a) {}
  abstract B<T,T> foo273();
  void bar273(A<? extends T> a){
    baz273(a.foo273());
  }


  <K> void baz274(B<?, ?> a) {}
  abstract B<T,? super T> foo274();
  void bar274(A<?> a){
    baz274(a.foo274());
  }


  <K> void baz275(B<?, ?> a) {}
  abstract B<T,? super T> foo275();
  void bar275(A<? super T> a){
    baz275(a.foo275());
  }


  <K> void baz276(B<?, ?> a) {}
  abstract B<T,? super T> foo276();
  void bar276(A<? extends T> a){
    baz276(a.foo276());
  }


  <K> void baz277(B<?, ?> a) {}
  abstract B<T,? extends T> foo277();
  void bar277(A<?> a){
    baz277(a.foo277());
  }


  <K> void baz278(B<?, ?> a) {}
  abstract B<T,? extends T> foo278();
  void bar278(A<? super T> a){
    baz278(a.foo278());
  }


  <K> void baz279(B<?, ?> a) {}
  abstract B<T,? extends T> foo279();
  void bar279(A<? extends T> a){
    baz279(a.foo279());
  }


  <K> void baz280(B<?, ?> a) {}
  abstract B<T,?> foo280();
  void bar280(A<?> a){
    baz280(a.foo280());
  }


  <K> void baz281(B<?, ?> a) {}
  abstract B<T,?> foo281();
  void bar281(A<? super T> a){
    baz281(a.foo281());
  }


  <K> void baz282(B<?, ?> a) {}
  abstract B<T,?> foo282();
  void bar282(A<? extends T> a){
    baz282(a.foo282());
  }


  <K> void baz283(B<?, ?> a) {}
  abstract B<? super T,? super T> foo283();
  void bar283(A<?> a){
    baz283(a.foo283());
  }


  <K> void baz284(B<?, ?> a) {}
  abstract B<? super T,? super T> foo284();
  void bar284(A<? super T> a){
    baz284(a.foo284());
  }


  <K> void baz285(B<?, ?> a) {}
  abstract B<? super T,? super T> foo285();
  void bar285(A<? extends T> a){
    baz285(a.foo285());
  }


  <K> void baz286(B<?, ?> a) {}
  abstract B<? super T,? extends T> foo286();
  void bar286(A<?> a){
    baz286(a.foo286());
  }


  <K> void baz287(B<?, ?> a) {}
  abstract B<? super T,? extends T> foo287();
  void bar287(A<? super T> a){
    baz287(a.foo287());
  }


  <K> void baz288(B<?, ?> a) {}
  abstract B<? super T,? extends T> foo288();
  void bar288(A<? extends T> a){
    baz288(a.foo288());
  }


  <K> void baz289(B<?, ?> a) {}
  abstract B<? super T,?> foo289();
  void bar289(A<?> a){
    baz289(a.foo289());
  }


  <K> void baz290(B<?, ?> a) {}
  abstract B<? super T,?> foo290();
  void bar290(A<? super T> a){
    baz290(a.foo290());
  }


  <K> void baz291(B<?, ?> a) {}
  abstract B<? super T,?> foo291();
  void bar291(A<? extends T> a){
    baz291(a.foo291());
  }


  <K> void baz292(B<?, ?> a) {}
  abstract B<? extends T,? extends T> foo292();
  void bar292(A<?> a){
    baz292(a.foo292());
  }


  <K> void baz293(B<?, ?> a) {}
  abstract B<? extends T,? extends T> foo293();
  void bar293(A<? super T> a){
    baz293(a.foo293());
  }


  <K> void baz294(B<?, ?> a) {}
  abstract B<? extends T,? extends T> foo294();
  void bar294(A<? extends T> a){
    baz294(a.foo294());
  }


  <K> void baz295(B<?, ?> a) {}
  abstract B<? extends T,?> foo295();
  void bar295(A<?> a){
    baz295(a.foo295());
  }


  <K> void baz296(B<?, ?> a) {}
  abstract B<? extends T,?> foo296();
  void bar296(A<? super T> a){
    baz296(a.foo296());
  }


  <K> void baz297(B<?, ?> a) {}
  abstract B<? extends T,?> foo297();
  void bar297(A<? extends T> a){
    baz297(a.foo297());
  }


  <K> void baz298(B<?, ?> a) {}
  abstract B<?,?> foo298();
  void bar298(A<?> a){
    baz298(a.foo298());
  }


  <K> void baz299(B<?, ?> a) {}
  abstract B<?,?> foo299();
  void bar299(A<? super T> a){
    baz299(a.foo299());
  }


  <K> void baz300(B<?, ?> a) {}
  abstract B<?,?> foo300();
  void bar300(A<? extends T> a){
    baz300(a.foo300());
  }

  /*
  //generation method
  public static void main(String[] args) {
      String prefix = "class B<S, R> {}\n" +
                      "abstract class A<T>  {\n";
      String template = "    <K> void baz$N$(B<$K1$, $K2$> a) {}\n" +
                        "    abstract B<$T1$,$T2$> foo$N$();\n" +
                        "    void bar$N$(A<$A1$> a){\n" +
                        "          baz$N$(a.foo$N$());\n" +
                        "    }\n";
      String suffix = "}";

      String[] k = {"K", "? extends K", "? super K", "?"};
      String[] t = {"T", "? extends T", "? super T", "?"};
      String[] a = {"? extends T", "? super T", "?"};

      System.out.println(prefix);
      int n = 1;
      for (int ki = 0; ki < k.length; ki++) {
          String k1 = k[ki];
          for (int kki = ki; kki < k.length; kki++) {
              String k2 = k[kki];
              for (int i = 0; i < t.length; i++) {
                  String t1 = t[i];
                  for (int it = i; it < t.length; it++) {
                      String t2 = t[it];
                      for (String a1 : a) {
                          System.out.println();
                          System.out.println(
                                  template.replaceAll("\\$K1\\$", k1)
                                          .replaceAll("\\$K2\\$", k2)
                                          .replaceAll("\\$T1\\$", t1)
                                          .replaceAll("\\$T2\\$", t2)
                                          .replaceAll("\\$A1\\$", a1)
                                          .replaceAll("\\$N\\$", String.valueOf(n++)));
                      }
                  }
              }
          }
      }
      System.out.println(suffix);
  }*/
}
