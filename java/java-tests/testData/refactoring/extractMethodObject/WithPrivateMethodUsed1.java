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
import java.util.Date;

public class TestClass {

    private Date expiry;
    private Date maturity;
    private int commitment;
    private double outstanding;
    private Iterable payments;
    private Date today;
    private Date start;
    private int riskRating;


    public double cal<caret>culate() {
        return ( outstandingRiskAmount() * duration() * riskFactor() ) + ( unusedRiskAmount() * duration() * unusedRiskFactor() );
    }

    private double riskFactor() {
        return 5.0;
    }

    private double unusedRiskFactor() {
        return 6.0;
    }

    private double unusedRiskAmount() {
        return ( commitment - outstanding );
    }

    private double outstandingRiskAmount() {
        return outstanding;
    }

    private double getUsedPercentage() {
        return 1.0;
    }

    private double duration() {
        if ( expiry == null && maturity != null ) {
            return 1.0;
        } else if ( expiry != null && maturity == null ) {
            return yearsTo( expiry );
        }
        return 0.0;

    }

    private double yearsTo( final Date endDate ) {
        Date beginDate = ( today == null ? start : today );
        return ( ( endDate.getTime() - beginDate.getTime() ));
    }

}