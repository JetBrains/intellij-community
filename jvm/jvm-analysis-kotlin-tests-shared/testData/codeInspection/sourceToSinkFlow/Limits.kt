import org.checkerframework.checker.tainting.qual.Untainted

object Limit {
  const val fromAnotherFile = Limit2.fromAnotherFile
  const val fromAnotherFile2 = Limit2.fromAnotherFile2
  val fromAnotherFile3 = Limit2.fromAnotherFile3
  val fromAnotherFile4 = Limit2.fromAnotherFile4
  val fromAnotherFile5 = Limit2().fromAnotherFile5
  val fromAnotherFile6 = Limit2().fromAnotherFile6
  fun test(clear: @Untainted String?, dirty: String) {
    sink(<warning descr="Unknown string is used as safe parameter">dirty</warning>) //warn
    sink(<weak_warning descr="Too complex to check that the string is safe in a safe context">next(next(next(next(next(next(next(next(next(next(next(next(next(next(next(dirty)))))))))))))))</weak_warning>) //warn
    sink(<warning descr="Unknown string is used as safe parameter">next(next(next(next(next(dirty)))))</warning>) //warn
    val nextVariable = next(next(next(next(clear))))
    sink(nextVariable)
    sink(nextVariable + nextVariable + nextVariable + nextVariable + nextVariable + nextVariable)
    sink(<warning descr="Unknown string is used as safe parameter">nextVariable + next(next(next(next(dirty))))</warning>) //warn
    sink(<warning descr="Unknown string is used as safe parameter">"${dirty} test dirty"</warning>) //warn
    sink("$clear test clear")
    sink(fromAnotherFile)
    sink(fromAnotherFile2) //not warn, because static and final fields are considered as safe
    sink(fromAnotherFile3) //not warn, because static and final fields are considered as safe
    sink(<warning descr="Unknown string is used as safe parameter">fromAnotherFile4</warning>) //warn
    sink(<warning descr="Unknown string is used as safe parameter">fromAnotherFile5</warning>) //warn because we don't process outer files
    sink(<warning descr="Unknown string is used as safe parameter">fromAnotherFile6</warning>) //warn
    val cleanLongString = "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          clear +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh"
    sink(cleanLongString)
    sink(<warning descr="Unknown string is used as safe parameter">cleanLongString + dirty</warning>) //warn
    val dirtyLongString = "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          dirty +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          clear +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh" +
                          "sdafjhasdfkhaskjdfh"
    sink( <warning descr="Unknown string is used as safe parameter">dirtyLongString</warning>) //warn
    val a1 = clear + 1 + clear + clear + clear + clear + clear + clear + clear
    val a2 = a1 + 1 + a1 + a1 + a1 + a1 + a1 + a1 + a1 + a1 + a1 + a1 + a1
    val a3 = a2 + 1 + a2 + a2 + a2 + a2 + a2 + a2 + a2 + a2 + a2 + a2 + a2
    val a4 = a3 + 1 + a3 + a3 + a3 + a3 + a3 + a3 + a3 + a3 + a3 + a3 + a3
    val a5 = a4 + 1 + a4 + a4 + a4 + a4 + a4 + a4 + a4 + a4 + a4 + a4 + a4
    val a6 = a5 + 1 + a5 + a5 + a5 + a5 + a5 + a5 + a5 + a5 + a5 + a5 + a5
    val a7 = a6 + 1 + a6 + a6 + a6 + a6 + a6 + a6 + a6 + a6 + a6 + a6 + a6
    val a8 = a7 + 1 + a7 + a7 + a7 + a7 + a7 + a7 + a7 + a7 + a7 + a7 + a7
    val a9 = a8 + 1 + a8 + a8 + a8 + a8 + a8 + a8 + a8 + a8 + a8 + a8 + a8
    val a10 = a9 + 1 + a9 + a9 + a9 + a9 + a9 + a9 + a9 + a9 + a9 + a9 + a9
    sink(<weak_warning descr="Too complex to check that the string is safe in a safe context">a10</weak_warning>) //warn
    sink(a2)
  }

  fun next(next: String?): String? {
    return next
  }

  fun sink(<warning descr="[UNUSED_PARAMETER] Parameter 'string' is never used">string</warning>: @Untainted String?) {}
}
