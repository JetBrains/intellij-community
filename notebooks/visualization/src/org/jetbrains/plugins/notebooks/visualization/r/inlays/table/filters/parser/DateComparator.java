/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.parser;

import java.text.Format;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;

/**
 * Class to deduce a sensible {@link Comparator} for {@link Date} instances.<br>
 */
abstract public class DateComparator implements Comparator<Date> {

    /**
     * Factory constructor, returning an instance suitable for the given format.
     */
    public static DateComparator getDateComparator(Format dateFormat) {
        // the idea is to build a date instance, change then each field
        // (milliseconds / seconds / etc) and check the change on the
        // parsed instance. If changing, for example, the seconds, does
        // not produce a different formatted string, the comparator will
        // not pay attention to the seconds, and so on
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(new Date().getTime());
        if (change(calendar, dateFormat, Calendar.MILLISECOND)) {
            // Milliseconds affect the output, full comparison
            return new DateComparator() {
                @Override public long diff(Date o1, Date o2) {
                    return o1.compareTo(o2);
                }
            };
        }

        int divisor = 0;
        if (change(calendar, dateFormat, Calendar.SECOND)) {
            divisor = 1000;
        } else if (change(calendar, dateFormat, Calendar.MINUTE)) {
            divisor = 1000 * 60;
        } else if (change(calendar, dateFormat, Calendar.HOUR)) {
            divisor = 1000 * 60 * 60;
        } else if (change(calendar, dateFormat, Calendar.DAY_OF_YEAR)) {
            return new DayMonthYearComparator(calendar);
        } else if (change(calendar, dateFormat, Calendar.MONTH)) {
            return new MonthYearComparator(calendar);
        } else if (change(calendar, dateFormat, Calendar.YEAR)) {
            return new YearComparator(calendar);
        } else {
            // nothing affects the output, great formatter!
            return new DateComparator() {
                @Override public long diff(Date o1, Date o2) {
                    return 0;
                }
            };
        }

        return new TimeComparator(divisor);
    }

    static private boolean change(Calendar c, Format f, int field) {
        c.set(field, 10);

        String sf = f.format(c.getTime());
        c.set(field, 11);

        return !sf.equals(f.format(c.getTime()));
    }

    @Override public int compare(Date o1, Date o2) {
        if (o1 == null) {
            return (o2 == null) ? 0 : -1;
        }

        if (o2 == null) {
            return 1;
        }

        long diff = diff(o1, o2);

        return (diff == 0) ? 0 : ((diff > 0) ? 1 : -1);
    }

    public abstract long diff(Date o1, Date o2);

    /**
     * DateComparator when the difference relies on time fields (seconds,
     * minutes, hours).<br>
     * We divide the time to set out the unneeded information, before comparing
     */
    static class TimeComparator extends DateComparator {
        int divisor;

        public TimeComparator(int divisor) {
            this.divisor = divisor;
        }

        @Override public long diff(Date o1, Date o2) {
            return (o1.getTime() / divisor) - (o2.getTime() / divisor);
        }
    }

    /** DateComparator that simply compares the year's fields. */
    static class YearComparator extends DateComparator {
        Calendar calendar;

        public YearComparator(Calendar calendar) {
            this.calendar = calendar;
        }

        @Override public long diff(Date o1, Date o2) {
            calendar.setTime(o1);

            long base = time();
            calendar.setTime(o2);

            return base - time();
        }

        long time() {
            return calendar.get(Calendar.YEAR);
        }
    }

    /** DateComparator that simply compares the year and month's fields. */
    static class MonthYearComparator extends YearComparator {
        public MonthYearComparator(Calendar calendar) {
            super(calendar);
        }

        @Override long time() {
            return (calendar.get(Calendar.YEAR) * 12L)
                    + calendar.get(Calendar.MONTH);
        }
    }

    /** DateComparator that simply compares the year/month/day's fields. */
    static class DayMonthYearComparator extends YearComparator {
        public DayMonthYearComparator(Calendar calendar) {
            super(calendar);
        }

        @Override long time() {
            return (calendar.get(Calendar.YEAR) * 400L)
                    + calendar.get(Calendar.DAY_OF_YEAR);
        }
    }
}
