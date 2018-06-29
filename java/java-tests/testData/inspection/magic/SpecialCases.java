/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.intellij.lang.annotations.MagicConstant;

import java.util.Calendar;

import static java.util.Calendar.*;

public class SpecialCases {
  @MagicConstant(intValues = {JANUARY, FEBRUARY, MARCH, APRIL, MAY, JUNE, JULY, AUGUST, SEPTEMBER, OCTOBER, NOVEMBER, DECEMBER})
  int getMonth(Calendar c) {
    return <warning descr="Should be one of: Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL, Calendar.MAY, ...">c.get(DAY_OF_WEEK)</warning>;
  }

  @MagicConstant(intValues = {JANUARY, FEBRUARY, MARCH, APRIL, MAY, JUNE, JULY, AUGUST, SEPTEMBER, OCTOBER, NOVEMBER, DECEMBER})
  int getMonthCorrect(Calendar c) {
    return c.get(MONTH);
  }

  void acceptMonth(@MagicConstant(intValues = {JANUARY, FEBRUARY, MARCH, APRIL, MAY, JUNE, JULY, AUGUST, SEPTEMBER, OCTOBER, NOVEMBER, DECEMBER}) int month) {}

  void testAccept(Calendar c) {
    acceptMonth(<warning descr="Should be one of: Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL, Calendar.MAY, ...">c.getFirstDayOfWeek()</warning>);
    acceptMonth(JANUARY);
    acceptMonth(<warning descr="Should be one of: Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL, Calendar.MAY, ...">c.get(DAY_OF_WEEK)</warning>);
    acceptMonth(c.get(MONTH));
  }

  void test() {
    Calendar instance = Calendar.getInstance();
    if(instance.get(Calendar.DAY_OF_WEEK) == MONDAY) {}
    if(instance.get(Calendar.DAY_OF_WEEK) == <warning descr="Should be one of: Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, ...">FEBRUARY</warning>) {}
    if(instance.get(Calendar.MONTH) == <warning descr="Should be one of: Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL, Calendar.MAY, ...">MONDAY</warning>) {}
    if(instance.get(Calendar.MONTH) == FEBRUARY) {}
    if(instance.get(Calendar.MONTH) == <warning descr="Should be one of: Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL, Calendar.MAY, ...">AM</warning>) {}
    if(instance.get(Calendar.AM_PM) == AM) {}
    if(instance.get(Calendar.AM_PM) == <warning descr="Should be one of: Calendar.AM, Calendar.PM">MONDAY</warning>) {}
    if(instance.get(Calendar.DATE) == 1) {}
  }
}