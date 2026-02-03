class B<S, R extends S> {}
abstract class A<T>  {


  <K> void baz1(B<K, K> a) {}
  abstract B<T,T> foo1();
  void bar1(A<? extends T> a){
    baz1(a.foo1());
  }


  <K> void baz2(B<K, K> a) {}
  abstract B<T,T> foo2();
  void bar2(A<? super T> a){
    baz2(a.foo2());
  }


  <K> void baz3(B<K, K> a) {}
  abstract B<T,T> foo3();
  void bar3(A<?> a){
    baz3(a.foo3());
  }


  <K> void baz4(B<K, K> a) {}
  abstract B<T,? extends T> foo4();
  void bar4(A<? extends T> a){
    baz4(a.foo4());
  }


  <K> void baz5(B<K, K> a) {}
  abstract B<T,? extends T> foo5();
  void bar5(A<? super T> a){
    baz5(a.foo5());
  }


  <K> void baz6(B<K, K> a) {}
  abstract B<T,? extends T> foo6();
  void bar6(A<?> a){
    baz6(a.foo6());
  }


  <K> void baz7(B<K, K> a) {}
  abstract B<T,? super T> foo7();
  void bar7(A<? extends T> a){
    baz7(a.foo7());
  }


  <K> void baz8(B<K, K> a) {}
  abstract B<T,? super T> foo8();
  void bar8(A<? super T> a){
    baz8(a.foo8());
  }


  <K> void baz9(B<K, K> a) {}
  abstract B<T,? super T> foo9();
  void bar9(A<?> a){
    baz9(a.foo9());
  }


  <K> void baz10(B<K, K> a) {}
  abstract B<T,?> foo10();
  void bar10(A<? extends T> a){
    baz10(a.foo10());
  }


  <K> void baz11(B<K, K> a) {}
  abstract B<T,?> foo11();
  void bar11(A<? super T> a){
    baz11(a.foo11());
  }


  <K> void baz12(B<K, K> a) {}
  abstract B<T,?> foo12();
  void bar12(A<?> a){
    baz12(a.foo12());
  }


  <K> void baz13(B<K, K> a) {}
  abstract B<? extends T,? extends T> foo13();
  void bar13(A<? extends T> a){
    baz13(a.foo13());
  }


  <K> void baz14(B<K, K> a) {}
  abstract B<? extends T,? extends T> foo14();
  void bar14(A<? super T> a){
    baz14(a.foo14());
  }


  <K> void baz15(B<K, K> a) {}
  abstract B<? extends T,? extends T> foo15();
  void bar15(A<?> a){
    baz15(a.foo15());
  }


  <K> void baz16(B<K, K> a) {}
  abstract B<? extends T,? super T> foo16();
  void bar16(A<? extends T> a){
    baz16(a.foo16());
  }


  <K> void baz17(B<K, K> a) {}
  abstract B<? extends T,? super T> foo17();
  void bar17(A<? super T> a){
    baz17(a.foo17());
  }


  <K> void baz18(B<K, K> a) {}
  abstract B<? extends T,? super T> foo18();
  void bar18(A<?> a){
    baz18(a.foo18());
  }


  <K> void baz19(B<K, K> a) {}
  abstract B<? extends T,?> foo19();
  void bar19(A<? extends T> a){
    baz19(a.foo19());
  }


  <K> void baz20(B<K, K> a) {}
  abstract B<? extends T,?> foo20();
  void bar20(A<? super T> a){
    baz20(a.foo20());
  }


  <K> void baz21(B<K, K> a) {}
  abstract B<? extends T,?> foo21();
  void bar21(A<?> a){
    baz21(a.foo21());
  }


  <K> void baz22(B<K, K> a) {}
  abstract B<? super T,? super T> foo22();
  void bar22(A<? extends T> a){
    baz22(a.foo22());
  }


  <K> void baz23(B<K, K> a) {}
  abstract B<? super T,? super T> foo23();
  void bar23(A<? super T> a){
    baz23(a.foo23());
  }


  <K> void baz24(B<K, K> a) {}
  abstract B<? super T,? super T> foo24();
  void bar24(A<?> a){
    baz24(a.foo24());
  }


  <K> void baz25(B<K, K> a) {}
  abstract B<? super T,?> foo25();
  void bar25(A<? extends T> a){
    baz25(a.foo25());
  }


  <K> void baz26(B<K, K> a) {}
  abstract B<? super T,?> foo26();
  void bar26(A<? super T> a){
    baz26(a.foo26());
  }


  <K> void baz27(B<K, K> a) {}
  abstract B<? super T,?> foo27();
  void bar27(A<?> a){
    baz27(a.foo27());
  }


  <K> void baz28(B<K, K> a) {}
  abstract B<?,?> foo28();
  void bar28(A<? extends T> a){
    baz28(a.foo28());
  }


  <K> void baz29(B<K, K> a) {}
  abstract B<?,?> foo29();
  void bar29(A<? super T> a){
    baz29(a.foo29());
  }


  <K> void baz30(B<K, K> a) {}
  abstract B<?,?> foo30();
  void bar30(A<?> a){
    baz30(a.foo30());
  }


  <K> void baz31(B<K, ? extends K> a) {}
  abstract B<T,T> foo31();
  void bar31(A<? extends T> a){
    baz31(a.foo31());
  }


  <K> void baz32(B<K, ? extends K> a) {}
  abstract B<T,T> foo32();
  void bar32(A<? super T> a){
    baz32(a.foo32());
  }


  <K> void baz33(B<K, ? extends K> a) {}
  abstract B<T,T> foo33();
  void bar33(A<?> a){
    baz33(a.foo33());
  }


  <K> void baz34(B<K, ? extends K> a) {}
  abstract B<T,? extends T> foo34();
  void bar34(A<? extends T> a){
    baz34(a.foo34());
  }


  <K> void baz35(B<K, ? extends K> a) {}
  abstract B<T,? extends T> foo35();
  void bar35(A<? super T> a){
    baz35(a.foo35());
  }


  <K> void baz36(B<K, ? extends K> a) {}
  abstract B<T,? extends T> foo36();
  void bar36(A<?> a){
    baz36(a.foo36());
  }


  <K> void baz37(B<K, ? extends K> a) {}
  abstract B<T,? super T> foo37();
  void bar37(A<? extends T> a){
    baz37(a.foo37());
  }


  <K> void baz38(B<K, ? extends K> a) {}
  abstract B<T,? super T> foo38();
  void bar38(A<? super T> a){
    baz38(a.foo38());
  }


  <K> void baz39(B<K, ? extends K> a) {}
  abstract B<T,? super T> foo39();
  void bar39(A<?> a){
    baz39(a.foo39());
  }


  <K> void baz40(B<K, ? extends K> a) {}
  abstract B<T,?> foo40();
  void bar40(A<? extends T> a){
    baz40(a.foo40());
  }


  <K> void baz41(B<K, ? extends K> a) {}
  abstract B<T,?> foo41();
  void bar41(A<? super T> a){
    baz41(a.foo41());
  }


  <K> void baz42(B<K, ? extends K> a) {}
  abstract B<T,?> foo42();
  void bar42(A<?> a){
    baz42(a.foo42());
  }


  <K> void baz43(B<K, ? extends K> a) {}
  abstract B<? extends T,? extends T> foo43();
  void bar43(A<? extends T> a){
    baz43(a.foo43());
  }


  <K> void baz44(B<K, ? extends K> a) {}
  abstract B<? extends T,? extends T> foo44();
  void bar44(A<? super T> a){
    baz44(a.foo44());
  }


  <K> void baz45(B<K, ? extends K> a) {}
  abstract B<? extends T,? extends T> foo45();
  void bar45(A<?> a){
    baz45(a.foo45());
  }


  <K> void baz46(B<K, ? extends K> a) {}
  abstract B<? extends T,? super T> foo46();
  void bar46(A<? extends T> a){
    baz46(a.foo46());
  }


  <K> void baz47(B<K, ? extends K> a) {}
  abstract B<? extends T,? super T> foo47();
  void bar47(A<? super T> a){
    baz47(a.foo47());
  }


  <K> void baz48(B<K, ? extends K> a) {}
  abstract B<? extends T,? super T> foo48();
  void bar48(A<?> a){
    baz48(a.foo48());
  }


  <K> void baz49(B<K, ? extends K> a) {}
  abstract B<? extends T,?> foo49();
  void bar49(A<? extends T> a){
    baz49(a.foo49());
  }


  <K> void baz50(B<K, ? extends K> a) {}
  abstract B<? extends T,?> foo50();
  void bar50(A<? super T> a){
    baz50(a.foo50());
  }


  <K> void baz51(B<K, ? extends K> a) {}
  abstract B<? extends T,?> foo51();
  void bar51(A<?> a){
    baz51(a.foo51());
  }


  <K> void baz52(B<K, ? extends K> a) {}
  abstract B<? super T,? super T> foo52();
  void bar52(A<? extends T> a){
    baz52(a.foo52());
  }


  <K> void baz53(B<K, ? extends K> a) {}
  abstract B<? super T,? super T> foo53();
  void bar53(A<? super T> a){
    baz53(a.foo53());
  }


  <K> void baz54(B<K, ? extends K> a) {}
  abstract B<? super T,? super T> foo54();
  void bar54(A<?> a){
    baz54(a.foo54());
  }


  <K> void baz55(B<K, ? extends K> a) {}
  abstract B<? super T,?> foo55();
  void bar55(A<? extends T> a){
    baz55(a.foo55());
  }


  <K> void baz56(B<K, ? extends K> a) {}
  abstract B<? super T,?> foo56();
  void bar56(A<? super T> a){
    baz56(a.foo56());
  }


  <K> void baz57(B<K, ? extends K> a) {}
  abstract B<? super T,?> foo57();
  void bar57(A<?> a){
    baz57(a.foo57());
  }


  <K> void baz58(B<K, ? extends K> a) {}
  abstract B<?,?> foo58();
  void bar58(A<? extends T> a){
    baz58(a.foo58());
  }


  <K> void baz59(B<K, ? extends K> a) {}
  abstract B<?,?> foo59();
  void bar59(A<? super T> a){
    baz59(a.foo59());
  }


  <K> void baz60(B<K, ? extends K> a) {}
  abstract B<?,?> foo60();
  void bar60(A<?> a){
    baz60(a.foo60());
  }


  <K> void baz61(B<K, ? super K> a) {}
  abstract B<T,T> foo61();
  void bar61(A<? extends T> a){
    baz61(a.foo61());
  }


  <K> void baz62(B<K, ? super K> a) {}
  abstract B<T,T> foo62();
  void bar62(A<? super T> a){
    baz62(a.foo62());
  }


  <K> void baz63(B<K, ? super K> a) {}
  abstract B<T,T> foo63();
  void bar63(A<?> a){
    baz63(a.foo63());
  }


  <K> void baz64(B<K, ? super K> a) {}
  abstract B<T,? extends T> foo64();
  void bar64(A<? extends T> a){
    baz64(a.foo64());
  }


  <K> void baz65(B<K, ? super K> a) {}
  abstract B<T,? extends T> foo65();
  void bar65(A<? super T> a){
    baz65(a.foo65());
  }


  <K> void baz66(B<K, ? super K> a) {}
  abstract B<T,? extends T> foo66();
  void bar66(A<?> a){
    baz66(a.foo66());
  }


  <K> void baz67(B<K, ? super K> a) {}
  abstract B<T,? super T> foo67();
  void bar67(A<? extends T> a){
    baz67(a.foo67());
  }


  <K> void baz68(B<K, ? super K> a) {}
  abstract B<T,? super T> foo68();
  void bar68(A<? super T> a){
    baz68(a.foo68());
  }


  <K> void baz69(B<K, ? super K> a) {}
  abstract B<T,? super T> foo69();
  void bar69(A<?> a){
    baz69(a.foo69());
  }


  <K> void baz70(B<K, ? super K> a) {}
  abstract B<T,?> foo70();
  void bar70(A<? extends T> a){
    baz70(a.foo70());
  }


  <K> void baz71(B<K, ? super K> a) {}
  abstract B<T,?> foo71();
  void bar71(A<? super T> a){
    baz71(a.foo71());
  }


  <K> void baz72(B<K, ? super K> a) {}
  abstract B<T,?> foo72();
  void bar72(A<?> a){
    baz72(a.foo72());
  }


  <K> void baz73(B<K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo73();
  void bar73(A<? extends T> a){
    baz73(a.foo73());
  }


  <K> void baz74(B<K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo74();
  void bar74(A<? super T> a){
    baz74(a.foo74());
  }


  <K> void baz75(B<K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo75();
  void bar75(A<?> a){
    baz75(a.foo75());
  }


  <K> void baz76(B<K, ? super K> a) {}
  abstract B<? extends T,? super T> foo76();
  void bar76(A<? extends T> a){
    baz76(a.foo76());
  }


  <K> void baz77(B<K, ? super K> a) {}
  abstract B<? extends T,? super T> foo77();
  void bar77(A<? super T> a){
    baz77(a.foo77());
  }


  <K> void baz78(B<K, ? super K> a) {}
  abstract B<? extends T,? super T> foo78();
  void bar78(A<?> a){
    baz78(a.foo78());
  }


  <K> void baz79(B<K, ? super K> a) {}
  abstract B<? extends T,?> foo79();
  void bar79(A<? extends T> a){
    baz79(a.foo79());
  }


  <K> void baz80(B<K, ? super K> a) {}
  abstract B<? extends T,?> foo80();
  void bar80(A<? super T> a){
    baz80(a.foo80());
  }


  <K> void baz81(B<K, ? super K> a) {}
  abstract B<? extends T,?> foo81();
  void bar81(A<?> a){
    baz81(a.foo81());
  }


  <K> void baz82(B<K, ? super K> a) {}
  abstract B<? super T,? super T> foo82();
  void bar82(A<? extends T> a){
    baz82(a.foo82());
  }


  <K> void baz83(B<K, ? super K> a) {}
  abstract B<? super T,? super T> foo83();
  void bar83(A<? super T> a){
    baz83(a.foo83());
  }


  <K> void baz84(B<K, ? super K> a) {}
  abstract B<? super T,? super T> foo84();
  void bar84(A<?> a){
    baz84(a.foo84());
  }


  <K> void baz85(B<K, ? super K> a) {}
  abstract B<? super T,?> foo85();
  void bar85(A<? extends T> a){
    baz85(a.foo85());
  }


  <K> void baz86(B<K, ? super K> a) {}
  abstract B<? super T,?> foo86();
  void bar86(A<? super T> a){
    baz86(a.foo86());
  }


  <K> void baz87(B<K, ? super K> a) {}
  abstract B<? super T,?> foo87();
  void bar87(A<?> a){
    baz87(a.foo87());
  }


  <K> void baz88(B<K, ? super K> a) {}
  abstract B<?,?> foo88();
  void bar88(A<? extends T> a){
    baz88(a.foo88());
  }


  <K> void baz89(B<K, ? super K> a) {}
  abstract B<?,?> foo89();
  void bar89(A<? super T> a){
    baz89(a.foo89());
  }


  <K> void baz90(B<K, ? super K> a) {}
  abstract B<?,?> foo90();
  void bar90(A<?> a){
    baz90(a.foo90());
  }


  <K> void baz91(B<K, ?> a) {}
  abstract B<T,T> foo91();
  void bar91(A<? extends T> a){
    baz91(a.foo91());
  }


  <K> void baz92(B<K, ?> a) {}
  abstract B<T,T> foo92();
  void bar92(A<? super T> a){
    baz92(a.foo92());
  }


  <K> void baz93(B<K, ?> a) {}
  abstract B<T,T> foo93();
  void bar93(A<?> a){
    baz93(a.foo93());
  }


  <K> void baz94(B<K, ?> a) {}
  abstract B<T,? extends T> foo94();
  void bar94(A<? extends T> a){
    baz94(a.foo94());
  }


  <K> void baz95(B<K, ?> a) {}
  abstract B<T,? extends T> foo95();
  void bar95(A<? super T> a){
    baz95(a.foo95());
  }


  <K> void baz96(B<K, ?> a) {}
  abstract B<T,? extends T> foo96();
  void bar96(A<?> a){
    baz96(a.foo96());
  }


  <K> void baz97(B<K, ?> a) {}
  abstract B<T,? super T> foo97();
  void bar97(A<? extends T> a){
    baz97(a.foo97());
  }


  <K> void baz98(B<K, ?> a) {}
  abstract B<T,? super T> foo98();
  void bar98(A<? super T> a){
    baz98(a.foo98());
  }


  <K> void baz99(B<K, ?> a) {}
  abstract B<T,? super T> foo99();
  void bar99(A<?> a){
    baz99(a.foo99());
  }


  <K> void baz100(B<K, ?> a) {}
  abstract B<T,?> foo100();
  void bar100(A<? extends T> a){
    baz100(a.foo100());
  }


  <K> void baz101(B<K, ?> a) {}
  abstract B<T,?> foo101();
  void bar101(A<? super T> a){
    baz101(a.foo101());
  }


  <K> void baz102(B<K, ?> a) {}
  abstract B<T,?> foo102();
  void bar102(A<?> a){
    baz102(a.foo102());
  }


  <K> void baz103(B<K, ?> a) {}
  abstract B<? extends T,? extends T> foo103();
  void bar103(A<? extends T> a){
    baz103(a.foo103());
  }


  <K> void baz104(B<K, ?> a) {}
  abstract B<? extends T,? extends T> foo104();
  void bar104(A<? super T> a){
    baz104(a.foo104());
  }


  <K> void baz105(B<K, ?> a) {}
  abstract B<? extends T,? extends T> foo105();
  void bar105(A<?> a){
    baz105(a.foo105());
  }


  <K> void baz106(B<K, ?> a) {}
  abstract B<? extends T,? super T> foo106();
  void bar106(A<? extends T> a){
    baz106(a.foo106());
  }


  <K> void baz107(B<K, ?> a) {}
  abstract B<? extends T,? super T> foo107();
  void bar107(A<? super T> a){
    baz107(a.foo107());
  }


  <K> void baz108(B<K, ?> a) {}
  abstract B<? extends T,? super T> foo108();
  void bar108(A<?> a){
    baz108(a.foo108());
  }


  <K> void baz109(B<K, ?> a) {}
  abstract B<? extends T,?> foo109();
  void bar109(A<? extends T> a){
    baz109(a.foo109());
  }


  <K> void baz110(B<K, ?> a) {}
  abstract B<? extends T,?> foo110();
  void bar110(A<? super T> a){
    baz110(a.foo110());
  }


  <K> void baz111(B<K, ?> a) {}
  abstract B<? extends T,?> foo111();
  void bar111(A<?> a){
    baz111(a.foo111());
  }


  <K> void baz112(B<K, ?> a) {}
  abstract B<? super T,? super T> foo112();
  void bar112(A<? extends T> a){
    baz112(a.foo112());
  }


  <K> void baz113(B<K, ?> a) {}
  abstract B<? super T,? super T> foo113();
  void bar113(A<? super T> a){
    baz113(a.foo113());
  }


  <K> void baz114(B<K, ?> a) {}
  abstract B<? super T,? super T> foo114();
  void bar114(A<?> a){
    baz114(a.foo114());
  }


  <K> void baz115(B<K, ?> a) {}
  abstract B<? super T,?> foo115();
  void bar115(A<? extends T> a){
    baz115(a.foo115());
  }


  <K> void baz116(B<K, ?> a) {}
  abstract B<? super T,?> foo116();
  void bar116(A<? super T> a){
    baz116(a.foo116());
  }


  <K> void baz117(B<K, ?> a) {}
  abstract B<? super T,?> foo117();
  void bar117(A<?> a){
    baz117(a.foo117());
  }


  <K> void baz118(B<K, ?> a) {}
  abstract B<?,?> foo118();
  void bar118(A<? extends T> a){
    baz118(a.foo118());
  }


  <K> void baz119(B<K, ?> a) {}
  abstract B<?,?> foo119();
  void bar119(A<? super T> a){
    baz119(a.foo119());
  }


  <K> void baz120(B<K, ?> a) {}
  abstract B<?,?> foo120();
  void bar120(A<?> a){
    baz120(a.foo120());
  }


  <K> void baz121(B<? extends K, ? extends K> a) {}
  abstract B<T,T> foo121();
  void bar121(A<? extends T> a){
    baz121(a.foo121());
  }


  <K> void baz122(B<? extends K, ? extends K> a) {}
  abstract B<T,T> foo122();
  void bar122(A<? super T> a){
    baz122(a.foo122());
  }


  <K> void baz123(B<? extends K, ? extends K> a) {}
  abstract B<T,T> foo123();
  void bar123(A<?> a){
    baz123(a.foo123());
  }


  <K> void baz124(B<? extends K, ? extends K> a) {}
  abstract B<T,? extends T> foo124();
  void bar124(A<? extends T> a){
    baz124(a.foo124());
  }


  <K> void baz125(B<? extends K, ? extends K> a) {}
  abstract B<T,? extends T> foo125();
  void bar125(A<? super T> a){
    baz125(a.foo125());
  }


  <K> void baz126(B<? extends K, ? extends K> a) {}
  abstract B<T,? extends T> foo126();
  void bar126(A<?> a){
    baz126(a.foo126());
  }


  <K> void baz127(B<? extends K, ? extends K> a) {}
  abstract B<T,? super T> foo127();
  void bar127(A<? extends T> a){
    baz127(a.foo127());
  }


  <K> void baz128(B<? extends K, ? extends K> a) {}
  abstract B<T,? super T> foo128();
  void bar128(A<? super T> a){
    baz128(a.foo128());
  }


  <K> void baz129(B<? extends K, ? extends K> a) {}
  abstract B<T,? super T> foo129();
  void bar129(A<?> a){
    baz129(a.foo129());
  }


  <K> void baz130(B<? extends K, ? extends K> a) {}
  abstract B<T,?> foo130();
  void bar130(A<? extends T> a){
    baz130(a.foo130());
  }


  <K> void baz131(B<? extends K, ? extends K> a) {}
  abstract B<T,?> foo131();
  void bar131(A<? super T> a){
    baz131(a.foo131());
  }


  <K> void baz132(B<? extends K, ? extends K> a) {}
  abstract B<T,?> foo132();
  void bar132(A<?> a){
    baz132(a.foo132());
  }


  <K> void baz133(B<? extends K, ? extends K> a) {}
  abstract B<? extends T,? extends T> foo133();
  void bar133(A<? extends T> a){
    baz133(a.foo133());
  }


  <K> void baz134(B<? extends K, ? extends K> a) {}
  abstract B<? extends T,? extends T> foo134();
  void bar134(A<? super T> a){
    baz134(a.foo134());
  }


  <K> void baz135(B<? extends K, ? extends K> a) {}
  abstract B<? extends T,? extends T> foo135();
  void bar135(A<?> a){
    baz135(a.foo135());
  }


  <K> void baz136(B<? extends K, ? extends K> a) {}
  abstract B<? extends T,? super T> foo136();
  void bar136(A<? extends T> a){
    baz136(a.foo136());
  }


  <K> void baz137(B<? extends K, ? extends K> a) {}
  abstract B<? extends T,? super T> foo137();
  void bar137(A<? super T> a){
    baz137(a.foo137());
  }


  <K> void baz138(B<? extends K, ? extends K> a) {}
  abstract B<? extends T,? super T> foo138();
  void bar138(A<?> a){
    baz138(a.foo138());
  }


  <K> void baz139(B<? extends K, ? extends K> a) {}
  abstract B<? extends T,?> foo139();
  void bar139(A<? extends T> a){
    baz139(a.foo139());
  }


  <K> void baz140(B<? extends K, ? extends K> a) {}
  abstract B<? extends T,?> foo140();
  void bar140(A<? super T> a){
    baz140(a.foo140());
  }


  <K> void baz141(B<? extends K, ? extends K> a) {}
  abstract B<? extends T,?> foo141();
  void bar141(A<?> a){
    baz141(a.foo141());
  }


  <K> void baz142(B<? extends K, ? extends K> a) {}
  abstract B<? super T,? super T> foo142();
  void bar142(A<? extends T> a){
    baz142(a.foo142());
  }


  <K> void baz143(B<? extends K, ? extends K> a) {}
  abstract B<? super T,? super T> foo143();
  void bar143(A<? super T> a){
    baz143(a.foo143());
  }


  <K> void baz144(B<? extends K, ? extends K> a) {}
  abstract B<? super T,? super T> foo144();
  void bar144(A<?> a){
    baz144(a.foo144());
  }


  <K> void baz145(B<? extends K, ? extends K> a) {}
  abstract B<? super T,?> foo145();
  void bar145(A<? extends T> a){
    baz145(a.foo145());
  }


  <K> void baz146(B<? extends K, ? extends K> a) {}
  abstract B<? super T,?> foo146();
  void bar146(A<? super T> a){
    baz146(a.foo146());
  }


  <K> void baz147(B<? extends K, ? extends K> a) {}
  abstract B<? super T,?> foo147();
  void bar147(A<?> a){
    baz147(a.foo147());
  }


  <K> void baz148(B<? extends K, ? extends K> a) {}
  abstract B<?,?> foo148();
  void bar148(A<? extends T> a){
    baz148(a.foo148());
  }


  <K> void baz149(B<? extends K, ? extends K> a) {}
  abstract B<?,?> foo149();
  void bar149(A<? super T> a){
    baz149(a.foo149());
  }


  <K> void baz150(B<? extends K, ? extends K> a) {}
  abstract B<?,?> foo150();
  void bar150(A<?> a){
    baz150(a.foo150());
  }


  <K> void baz151(B<? extends K, ? super K> a) {}
  abstract B<T,T> foo151();
  void bar151(A<? extends T> a){
    baz151(a.foo151());
  }


  <K> void baz152(B<? extends K, ? super K> a) {}
  abstract B<T,T> foo152();
  void bar152(A<? super T> a){
    baz152(a.foo152());
  }


  <K> void baz153(B<? extends K, ? super K> a) {}
  abstract B<T,T> foo153();
  void bar153(A<?> a){
    baz153(a.foo153());
  }


  <K> void baz154(B<? extends K, ? super K> a) {}
  abstract B<T,? extends T> foo154();
  void bar154(A<? extends T> a){
    baz154(a.foo154());
  }


  <K> void baz155(B<? extends K, ? super K> a) {}
  abstract B<T,? extends T> foo155();
  void bar155(A<? super T> a){
    baz155(a.foo155());
  }


  <K> void baz156(B<? extends K, ? super K> a) {}
  abstract B<T,? extends T> foo156();
  void bar156(A<?> a){
    baz156(a.foo156());
  }


  <K> void baz157(B<? extends K, ? super K> a) {}
  abstract B<T,? super T> foo157();
  void bar157(A<? extends T> a){
    baz157(a.foo157());
  }


  <K> void baz158(B<? extends K, ? super K> a) {}
  abstract B<T,? super T> foo158();
  void bar158(A<? super T> a){
    baz158(a.foo158());
  }


  <K> void baz159(B<? extends K, ? super K> a) {}
  abstract B<T,? super T> foo159();
  void bar159(A<?> a){
    baz159(a.foo159());
  }


  <K> void baz160(B<? extends K, ? super K> a) {}
  abstract B<T,?> foo160();
  void bar160(A<? extends T> a){
    baz160(a.foo160());
  }


  <K> void baz161(B<? extends K, ? super K> a) {}
  abstract B<T,?> foo161();
  void bar161(A<? super T> a){
    baz161(a.foo161());
  }


  <K> void baz162(B<? extends K, ? super K> a) {}
  abstract B<T,?> foo162();
  void bar162(A<?> a){
    baz162(a.foo162());
  }


  <K> void baz163(B<? extends K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo163();
  void bar163(A<? extends T> a){
    baz163(a.foo163());
  }


  <K> void baz164(B<? extends K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo164();
  void bar164(A<? super T> a){
    baz164(a.foo164());
  }


  <K> void baz165(B<? extends K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo165();
  void bar165(A<?> a){
    baz165(a.foo165());
  }


  <K> void baz166(B<? extends K, ? super K> a) {}
  abstract B<? extends T,? super T> foo166();
  void bar166(A<? extends T> a){
    baz166(a.foo166());
  }


  <K> void baz167(B<? extends K, ? super K> a) {}
  abstract B<? extends T,? super T> foo167();
  void bar167(A<? super T> a){
    baz167(a.foo167());
  }


  <K> void baz168(B<? extends K, ? super K> a) {}
  abstract B<? extends T,? super T> foo168();
  void bar168(A<?> a){
    baz168(a.foo168());
  }


  <K> void baz169(B<? extends K, ? super K> a) {}
  abstract B<? extends T,?> foo169();
  void bar169(A<? extends T> a){
    baz169(a.foo169());
  }


  <K> void baz170(B<? extends K, ? super K> a) {}
  abstract B<? extends T,?> foo170();
  void bar170(A<? super T> a){
    baz170(a.foo170());
  }


  <K> void baz171(B<? extends K, ? super K> a) {}
  abstract B<? extends T,?> foo171();
  void bar171(A<?> a){
    baz171(a.foo171());
  }


  <K> void baz172(B<? extends K, ? super K> a) {}
  abstract B<? super T,? super T> foo172();
  void bar172(A<? extends T> a){
    baz172(a.foo172());
  }


  <K> void baz173(B<? extends K, ? super K> a) {}
  abstract B<? super T,? super T> foo173();
  void bar173(A<? super T> a){
    baz173(a.foo173());
  }


  <K> void baz174(B<? extends K, ? super K> a) {}
  abstract B<? super T,? super T> foo174();
  void bar174(A<?> a){
    baz174(a.foo174());
  }


  <K> void baz175(B<? extends K, ? super K> a) {}
  abstract B<? super T,?> foo175();
  void bar175(A<? extends T> a){
    baz175(a.foo175());
  }


  <K> void baz176(B<? extends K, ? super K> a) {}
  abstract B<? super T,?> foo176();
  void bar176(A<? super T> a){
    baz176(a.foo176());
  }


  <K> void baz177(B<? extends K, ? super K> a) {}
  abstract B<? super T,?> foo177();
  void bar177(A<?> a){
    baz177(a.foo177());
  }


  <K> void baz178(B<? extends K, ? super K> a) {}
  abstract B<?,?> foo178();
  void bar178(A<? extends T> a){
    baz178(a.foo178());
  }


  <K> void baz179(B<? extends K, ? super K> a) {}
  abstract B<?,?> foo179();
  void bar179(A<? super T> a){
    baz179(a.foo179());
  }


  <K> void baz180(B<? extends K, ? super K> a) {}
  abstract B<?,?> foo180();
  void bar180(A<?> a){
    baz180(a.foo180());
  }


  <K> void baz181(B<? extends K, ?> a) {}
  abstract B<T,T> foo181();
  void bar181(A<? extends T> a){
    baz181(a.foo181());
  }


  <K> void baz182(B<? extends K, ?> a) {}
  abstract B<T,T> foo182();
  void bar182(A<? super T> a){
    baz182(a.foo182());
  }


  <K> void baz183(B<? extends K, ?> a) {}
  abstract B<T,T> foo183();
  void bar183(A<?> a){
    baz183(a.foo183());
  }


  <K> void baz184(B<? extends K, ?> a) {}
  abstract B<T,? extends T> foo184();
  void bar184(A<? extends T> a){
    baz184(a.foo184());
  }


  <K> void baz185(B<? extends K, ?> a) {}
  abstract B<T,? extends T> foo185();
  void bar185(A<? super T> a){
    baz185(a.foo185());
  }


  <K> void baz186(B<? extends K, ?> a) {}
  abstract B<T,? extends T> foo186();
  void bar186(A<?> a){
    baz186(a.foo186());
  }


  <K> void baz187(B<? extends K, ?> a) {}
  abstract B<T,? super T> foo187();
  void bar187(A<? extends T> a){
    baz187(a.foo187());
  }


  <K> void baz188(B<? extends K, ?> a) {}
  abstract B<T,? super T> foo188();
  void bar188(A<? super T> a){
    baz188(a.foo188());
  }


  <K> void baz189(B<? extends K, ?> a) {}
  abstract B<T,? super T> foo189();
  void bar189(A<?> a){
    baz189(a.foo189());
  }


  <K> void baz190(B<? extends K, ?> a) {}
  abstract B<T,?> foo190();
  void bar190(A<? extends T> a){
    baz190(a.foo190());
  }


  <K> void baz191(B<? extends K, ?> a) {}
  abstract B<T,?> foo191();
  void bar191(A<? super T> a){
    baz191(a.foo191());
  }


  <K> void baz192(B<? extends K, ?> a) {}
  abstract B<T,?> foo192();
  void bar192(A<?> a){
    baz192(a.foo192());
  }


  <K> void baz193(B<? extends K, ?> a) {}
  abstract B<? extends T,? extends T> foo193();
  void bar193(A<? extends T> a){
    baz193(a.foo193());
  }


  <K> void baz194(B<? extends K, ?> a) {}
  abstract B<? extends T,? extends T> foo194();
  void bar194(A<? super T> a){
    baz194(a.foo194());
  }


  <K> void baz195(B<? extends K, ?> a) {}
  abstract B<? extends T,? extends T> foo195();
  void bar195(A<?> a){
    baz195(a.foo195());
  }


  <K> void baz196(B<? extends K, ?> a) {}
  abstract B<? extends T,? super T> foo196();
  void bar196(A<? extends T> a){
    baz196(a.foo196());
  }


  <K> void baz197(B<? extends K, ?> a) {}
  abstract B<? extends T,? super T> foo197();
  void bar197(A<? super T> a){
    baz197(a.foo197());
  }


  <K> void baz198(B<? extends K, ?> a) {}
  abstract B<? extends T,? super T> foo198();
  void bar198(A<?> a){
    baz198(a.foo198());
  }


  <K> void baz199(B<? extends K, ?> a) {}
  abstract B<? extends T,?> foo199();
  void bar199(A<? extends T> a){
    baz199(a.foo199());
  }


  <K> void baz200(B<? extends K, ?> a) {}
  abstract B<? extends T,?> foo200();
  void bar200(A<? super T> a){
    baz200(a.foo200());
  }


  <K> void baz201(B<? extends K, ?> a) {}
  abstract B<? extends T,?> foo201();
  void bar201(A<?> a){
    baz201(a.foo201());
  }


  <K> void baz202(B<? extends K, ?> a) {}
  abstract B<? super T,? super T> foo202();
  void bar202(A<? extends T> a){
    baz202(a.foo202());
  }


  <K> void baz203(B<? extends K, ?> a) {}
  abstract B<? super T,? super T> foo203();
  void bar203(A<? super T> a){
    baz203(a.foo203());
  }


  <K> void baz204(B<? extends K, ?> a) {}
  abstract B<? super T,? super T> foo204();
  void bar204(A<?> a){
    baz204(a.foo204());
  }


  <K> void baz205(B<? extends K, ?> a) {}
  abstract B<? super T,?> foo205();
  void bar205(A<? extends T> a){
    baz205(a.foo205());
  }


  <K> void baz206(B<? extends K, ?> a) {}
  abstract B<? super T,?> foo206();
  void bar206(A<? super T> a){
    baz206(a.foo206());
  }


  <K> void baz207(B<? extends K, ?> a) {}
  abstract B<? super T,?> foo207();
  void bar207(A<?> a){
    baz207(a.foo207());
  }


  <K> void baz208(B<? extends K, ?> a) {}
  abstract B<?,?> foo208();
  void bar208(A<? extends T> a){
    baz208(a.foo208());
  }


  <K> void baz209(B<? extends K, ?> a) {}
  abstract B<?,?> foo209();
  void bar209(A<? super T> a){
    baz209(a.foo209());
  }


  <K> void baz210(B<? extends K, ?> a) {}
  abstract B<?,?> foo210();
  void bar210(A<?> a){
    baz210(a.foo210());
  }


  <K> void baz211(B<? super K, ? super K> a) {}
  abstract B<T,T> foo211();
  void bar211(A<? extends T> a){
    baz211(a.foo211());
  }


  <K> void baz212(B<? super K, ? super K> a) {}
  abstract B<T,T> foo212();
  void bar212(A<? super T> a){
    baz212(a.foo212());
  }


  <K> void baz213(B<? super K, ? super K> a) {}
  abstract B<T,T> foo213();
  void bar213(A<?> a){
    baz213(a.foo213());
  }


  <K> void baz214(B<? super K, ? super K> a) {}
  abstract B<T,? extends T> foo214();
  void bar214(A<? extends T> a){
    baz214(a.foo214());
  }


  <K> void baz215(B<? super K, ? super K> a) {}
  abstract B<T,? extends T> foo215();
  void bar215(A<? super T> a){
    baz215(a.foo215());
  }


  <K> void baz216(B<? super K, ? super K> a) {}
  abstract B<T,? extends T> foo216();
  void bar216(A<?> a){
    baz216(a.foo216());
  }


  <K> void baz217(B<? super K, ? super K> a) {}
  abstract B<T,? super T> foo217();
  void bar217(A<? extends T> a){
    baz217(a.foo217());
  }


  <K> void baz218(B<? super K, ? super K> a) {}
  abstract B<T,? super T> foo218();
  void bar218(A<? super T> a){
    baz218(a.foo218());
  }


  <K> void baz219(B<? super K, ? super K> a) {}
  abstract B<T,? super T> foo219();
  void bar219(A<?> a){
    baz219(a.foo219());
  }


  <K> void baz220(B<? super K, ? super K> a) {}
  abstract B<T,?> foo220();
  void bar220(A<? extends T> a){
    baz220(a.foo220());
  }


  <K> void baz221(B<? super K, ? super K> a) {}
  abstract B<T,?> foo221();
  void bar221(A<? super T> a){
    baz221(a.foo221());
  }


  <K> void baz222(B<? super K, ? super K> a) {}
  abstract B<T,?> foo222();
  void bar222(A<?> a){
    baz222(a.foo222());
  }


  <K> void baz223(B<? super K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo223();
  void bar223(A<? extends T> a){
    baz223(a.foo223());
  }


  <K> void baz224(B<? super K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo224();
  void bar224(A<? super T> a){
    baz224(a.foo224());
  }


  <K> void baz225(B<? super K, ? super K> a) {}
  abstract B<? extends T,? extends T> foo225();
  void bar225(A<?> a){
    baz225(a.foo225());
  }


  <K> void baz226(B<? super K, ? super K> a) {}
  abstract B<? extends T,? super T> foo226();
  void bar226(A<? extends T> a){
    baz226(a.foo226());
  }


  <K> void baz227(B<? super K, ? super K> a) {}
  abstract B<? extends T,? super T> foo227();
  void bar227(A<? super T> a){
    baz227(a.foo227());
  }


  <K> void baz228(B<? super K, ? super K> a) {}
  abstract B<? extends T,? super T> foo228();
  void bar228(A<?> a){
    baz228(a.foo228());
  }


  <K> void baz229(B<? super K, ? super K> a) {}
  abstract B<? extends T,?> foo229();
  void bar229(A<? extends T> a){
    baz229(a.foo229());
  }


  <K> void baz230(B<? super K, ? super K> a) {}
  abstract B<? extends T,?> foo230();
  void bar230(A<? super T> a){
    baz230(a.foo230());
  }


  <K> void baz231(B<? super K, ? super K> a) {}
  abstract B<? extends T,?> foo231();
  void bar231(A<?> a){
    baz231(a.foo231());
  }


  <K> void baz232(B<? super K, ? super K> a) {}
  abstract B<? super T,? super T> foo232();
  void bar232(A<? extends T> a){
    baz232(a.foo232());
  }


  <K> void baz233(B<? super K, ? super K> a) {}
  abstract B<? super T,? super T> foo233();
  void bar233(A<? super T> a){
    baz233(a.foo233());
  }


  <K> void baz234(B<? super K, ? super K> a) {}
  abstract B<? super T,? super T> foo234();
  void bar234(A<?> a){
    baz234(a.foo234());
  }


  <K> void baz235(B<? super K, ? super K> a) {}
  abstract B<? super T,?> foo235();
  void bar235(A<? extends T> a){
    baz235(a.foo235());
  }


  <K> void baz236(B<? super K, ? super K> a) {}
  abstract B<? super T,?> foo236();
  void bar236(A<? super T> a){
    baz236(a.foo236());
  }


  <K> void baz237(B<? super K, ? super K> a) {}
  abstract B<? super T,?> foo237();
  void bar237(A<?> a){
    baz237(a.foo237());
  }


  <K> void baz238(B<? super K, ? super K> a) {}
  abstract B<?,?> foo238();
  void bar238(A<? extends T> a){
    baz238(a.foo238());
  }


  <K> void baz239(B<? super K, ? super K> a) {}
  abstract B<?,?> foo239();
  void bar239(A<? super T> a){
    baz239(a.foo239());
  }


  <K> void baz240(B<? super K, ? super K> a) {}
  abstract B<?,?> foo240();
  void bar240(A<?> a){
    baz240(a.foo240());
  }


  <K> void baz241(B<? super K, ?> a) {}
  abstract B<T,T> foo241();
  void bar241(A<? extends T> a){
    baz241(a.foo241());
  }


  <K> void baz242(B<? super K, ?> a) {}
  abstract B<T,T> foo242();
  void bar242(A<? super T> a){
    baz242(a.foo242());
  }


  <K> void baz243(B<? super K, ?> a) {}
  abstract B<T,T> foo243();
  void bar243(A<?> a){
    baz243(a.foo243());
  }


  <K> void baz244(B<? super K, ?> a) {}
  abstract B<T,? extends T> foo244();
  void bar244(A<? extends T> a){
    baz244(a.foo244());
  }


  <K> void baz245(B<? super K, ?> a) {}
  abstract B<T,? extends T> foo245();
  void bar245(A<? super T> a){
    baz245(a.foo245());
  }


  <K> void baz246(B<? super K, ?> a) {}
  abstract B<T,? extends T> foo246();
  void bar246(A<?> a){
    baz246(a.foo246());
  }


  <K> void baz247(B<? super K, ?> a) {}
  abstract B<T,? super T> foo247();
  void bar247(A<? extends T> a){
    baz247(a.foo247());
  }


  <K> void baz248(B<? super K, ?> a) {}
  abstract B<T,? super T> foo248();
  void bar248(A<? super T> a){
    baz248(a.foo248());
  }


  <K> void baz249(B<? super K, ?> a) {}
  abstract B<T,? super T> foo249();
  void bar249(A<?> a){
    baz249(a.foo249());
  }


  <K> void baz250(B<? super K, ?> a) {}
  abstract B<T,?> foo250();
  void bar250(A<? extends T> a){
    baz250(a.foo250());
  }


  <K> void baz251(B<? super K, ?> a) {}
  abstract B<T,?> foo251();
  void bar251(A<? super T> a){
    baz251(a.foo251());
  }


  <K> void baz252(B<? super K, ?> a) {}
  abstract B<T,?> foo252();
  void bar252(A<?> a){
    baz252(a.foo252());
  }


  <K> void baz253(B<? super K, ?> a) {}
  abstract B<? extends T,? extends T> foo253();
  void bar253(A<? extends T> a){
    baz253(a.foo253());
  }


  <K> void baz254(B<? super K, ?> a) {}
  abstract B<? extends T,? extends T> foo254();
  void bar254(A<? super T> a){
    baz254(a.foo254());
  }


  <K> void baz255(B<? super K, ?> a) {}
  abstract B<? extends T,? extends T> foo255();
  void bar255(A<?> a){
    baz255(a.foo255());
  }


  <K> void baz256(B<? super K, ?> a) {}
  abstract B<? extends T,? super T> foo256();
  void bar256(A<? extends T> a){
    baz256(a.foo256());
  }


  <K> void baz257(B<? super K, ?> a) {}
  abstract B<? extends T,? super T> foo257();
  void bar257(A<? super T> a){
    baz257(a.foo257());
  }


  <K> void baz258(B<? super K, ?> a) {}
  abstract B<? extends T,? super T> foo258();
  void bar258(A<?> a){
    baz258(a.foo258());
  }


  <K> void baz259(B<? super K, ?> a) {}
  abstract B<? extends T,?> foo259();
  void bar259(A<? extends T> a){
    baz259(a.foo259());
  }


  <K> void baz260(B<? super K, ?> a) {}
  abstract B<? extends T,?> foo260();
  void bar260(A<? super T> a){
    baz260(a.foo260());
  }


  <K> void baz261(B<? super K, ?> a) {}
  abstract B<? extends T,?> foo261();
  void bar261(A<?> a){
    baz261(a.foo261());
  }


  <K> void baz262(B<? super K, ?> a) {}
  abstract B<? super T,? super T> foo262();
  void bar262(A<? extends T> a){
    baz262(a.foo262());
  }


  <K> void baz263(B<? super K, ?> a) {}
  abstract B<? super T,? super T> foo263();
  void bar263(A<? super T> a){
    baz263(a.foo263());
  }


  <K> void baz264(B<? super K, ?> a) {}
  abstract B<? super T,? super T> foo264();
  void bar264(A<?> a){
    baz264(a.foo264());
  }


  <K> void baz265(B<? super K, ?> a) {}
  abstract B<? super T,?> foo265();
  void bar265(A<? extends T> a){
    baz265(a.foo265());
  }


  <K> void baz266(B<? super K, ?> a) {}
  abstract B<? super T,?> foo266();
  void bar266(A<? super T> a){
    baz266(a.foo266());
  }


  <K> void baz267(B<? super K, ?> a) {}
  abstract B<? super T,?> foo267();
  void bar267(A<?> a){
    baz267(a.foo267());
  }


  <K> void baz268(B<? super K, ?> a) {}
  abstract B<?,?> foo268();
  void bar268(A<? extends T> a){
    baz268(a.foo268());
  }


  <K> void baz269(B<? super K, ?> a) {}
  abstract B<?,?> foo269();
  void bar269(A<? super T> a){
    baz269(a.foo269());
  }


  <K> void baz270(B<? super K, ?> a) {}
  abstract B<?,?> foo270();
  void bar270(A<?> a){
    baz270(a.foo270());
  }


  <K> void baz271(B<?, ?> a) {}
  abstract B<T,T> foo271();
  void bar271(A<? extends T> a){
    baz271(a.foo271());
  }


  <K> void baz272(B<?, ?> a) {}
  abstract B<T,T> foo272();
  void bar272(A<? super T> a){
    baz272(a.foo272());
  }


  <K> void baz273(B<?, ?> a) {}
  abstract B<T,T> foo273();
  void bar273(A<?> a){
    baz273(a.foo273());
  }


  <K> void baz274(B<?, ?> a) {}
  abstract B<T,? extends T> foo274();
  void bar274(A<? extends T> a){
    baz274(a.foo274());
  }


  <K> void baz275(B<?, ?> a) {}
  abstract B<T,? extends T> foo275();
  void bar275(A<? super T> a){
    baz275(a.foo275());
  }


  <K> void baz276(B<?, ?> a) {}
  abstract B<T,? extends T> foo276();
  void bar276(A<?> a){
    baz276(a.foo276());
  }


  <K> void baz277(B<?, ?> a) {}
  abstract B<T,? super T> foo277();
  void bar277(A<? extends T> a){
    baz277(a.foo277());
  }


  <K> void baz278(B<?, ?> a) {}
  abstract B<T,? super T> foo278();
  void bar278(A<? super T> a){
    baz278(a.foo278());
  }


  <K> void baz279(B<?, ?> a) {}
  abstract B<T,? super T> foo279();
  void bar279(A<?> a){
    baz279(a.foo279());
  }


  <K> void baz280(B<?, ?> a) {}
  abstract B<T,?> foo280();
  void bar280(A<? extends T> a){
    baz280(a.foo280());
  }


  <K> void baz281(B<?, ?> a) {}
  abstract B<T,?> foo281();
  void bar281(A<? super T> a){
    baz281(a.foo281());
  }


  <K> void baz282(B<?, ?> a) {}
  abstract B<T,?> foo282();
  void bar282(A<?> a){
    baz282(a.foo282());
  }


  <K> void baz283(B<?, ?> a) {}
  abstract B<? extends T,? extends T> foo283();
  void bar283(A<? extends T> a){
    baz283(a.foo283());
  }


  <K> void baz284(B<?, ?> a) {}
  abstract B<? extends T,? extends T> foo284();
  void bar284(A<? super T> a){
    baz284(a.foo284());
  }


  <K> void baz285(B<?, ?> a) {}
  abstract B<? extends T,? extends T> foo285();
  void bar285(A<?> a){
    baz285(a.foo285());
  }


  <K> void baz286(B<?, ?> a) {}
  abstract B<? extends T,? super T> foo286();
  void bar286(A<? extends T> a){
    baz286(a.foo286());
  }


  <K> void baz287(B<?, ?> a) {}
  abstract B<? extends T,? super T> foo287();
  void bar287(A<? super T> a){
    baz287(a.foo287());
  }


  <K> void baz288(B<?, ?> a) {}
  abstract B<? extends T,? super T> foo288();
  void bar288(A<?> a){
    baz288(a.foo288());
  }


  <K> void baz289(B<?, ?> a) {}
  abstract B<? extends T,?> foo289();
  void bar289(A<? extends T> a){
    baz289(a.foo289());
  }


  <K> void baz290(B<?, ?> a) {}
  abstract B<? extends T,?> foo290();
  void bar290(A<? super T> a){
    baz290(a.foo290());
  }


  <K> void baz291(B<?, ?> a) {}
  abstract B<? extends T,?> foo291();
  void bar291(A<?> a){
    baz291(a.foo291());
  }


  <K> void baz292(B<?, ?> a) {}
  abstract B<? super T,? super T> foo292();
  void bar292(A<? extends T> a){
    baz292(a.foo292());
  }


  <K> void baz293(B<?, ?> a) {}
  abstract B<? super T,? super T> foo293();
  void bar293(A<? super T> a){
    baz293(a.foo293());
  }


  <K> void baz294(B<?, ?> a) {}
  abstract B<? super T,? super T> foo294();
  void bar294(A<?> a){
    baz294(a.foo294());
  }


  <K> void baz295(B<?, ?> a) {}
  abstract B<? super T,?> foo295();
  void bar295(A<? extends T> a){
    baz295(a.foo295());
  }


  <K> void baz296(B<?, ?> a) {}
  abstract B<? super T,?> foo296();
  void bar296(A<? super T> a){
    baz296(a.foo296());
  }


  <K> void baz297(B<?, ?> a) {}
  abstract B<? super T,?> foo297();
  void bar297(A<?> a){
    baz297(a.foo297());
  }


  <K> void baz298(B<?, ?> a) {}
  abstract B<?,?> foo298();
  void bar298(A<? extends T> a){
    baz298(a.foo298());
  }


  <K> void baz299(B<?, ?> a) {}
  abstract B<?,?> foo299();
  void bar299(A<? super T> a){
    baz299(a.foo299());
  }


  <K> void baz300(B<?, ?> a) {}
  abstract B<?,?> foo300();
  void bar300(A<?> a){
    baz300(a.foo300());
  }

  /*
  //generation method
  public static void main(String[] args) {
      String prefix = "class B<S, R extends S> {}\n" +
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
