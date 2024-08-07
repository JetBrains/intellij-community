/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.java.psi;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

public class TreeIsCorrectAfterDiffReparseTest extends LightJavaCodeInsightTestCase {

  public void testIDEADEV41862() throws Throwable {
    @NonNls String part1 = """
      package com.test;


      //------------------------------------------------------------------
      // Copyright (c) 1999, 2007
      // WorkForce Software, Inc.
      // All rights reserved.
      //
      // Web-site: http://www.workforcesoftware.com
      // E-mail:   support@workforcesoftware.com
      // Phone:    (877) 493-6723
      //
      // This program is protected by copyright laws and is considered
      // a trade secret of WorkForce Software.  Access to this program
      // and source code is granted only to licensed customers.  Under
      // no circumstances may this software or source code be distributed
      // without the prior written consent of WorkForce Software.
      // -----------------------------------------------------------------

      import com.workforcesoftware.Data.Employee.*;
      import com.workforcesoftware.Data.*;
      import com.workforcesoftware.Data.assignment.Asgnmt_master;
      import com.workforcesoftware.Data.assignment.Asgnmt;
      import com.workforcesoftware.Data.Output.Time_sheet_output;
      import com.workforcesoftware.Data.TimeSched.PayPeriodData;
      import com.workforcesoftware.Data.TimeSched.TimeSchedUtils;
      import com.workforcesoftware.Data.TimeSched.Schedule.Schedule_detailList;
      import com.workforcesoftware.Data.TimeSched.TimeSheet.*;
      import com.workforcesoftware.Data.assignment.Asgnmt_masterList;
      import com.workforcesoftware.Exceptions.*;
      import com.workforcesoftware.Gen.Choice.Approval_event_type;
      import com.workforcesoftware.Gen.Choice.Transaction_status;
      import com.workforcesoftware.Gen.Choice.Messages;
      import com.workforcesoftware.Gen.Choice.Program_source;
      import com.workforcesoftware.Gen.Other.DbRec.DbRecTime_sheet_output;
      import com.workforcesoftware.Gen.Other.DbRec.DbRecTime_sheet;
      import com.workforcesoftware.Gen.Other.List.WDateList;
      import com.workforcesoftware.Gen.Policy.Right_grp;
      import com.workforcesoftware.Gen.Policy.Policy_profile;
      import com.workforcesoftware.Policy.*;
      import com.workforcesoftware.Util.DateTime.WDate;
      import com.workforcesoftware.Util.DateTime.WDateTime;
      import com.workforcesoftware.Util.DB.ListWriter;
      import com.workforcesoftware.Util.DB.DbRecFieldCopier;
      import com.workforcesoftware.ClientRequests.TimeSched.TimeSchedUtil;
      import com.workforcesoftware.ClientRequests.TimeSched.PayPeriodInfo;
      import com.workforcesoftware.ClientRequests.TimeEntry.ApprovalInfo;
      import com.workforcesoftware.ClientRequests.TimeEntry.BankBalanceResults;
      import com.workforcesoftware.Misc.ServerErrorLogger;
      import com.workforcesoftware.Misc.ServerError;
      import com.workforcesoftware.AssignmentPeriod.*;
      import com.workforcesoftware.AssignmentPeriod.TimeSheetState;
      import com.workforcesoftware.Dictionary.DataDictionary;

      import java.sql.SQLException;
      import java.util.*;
      import org.log4j.Category;

      /**
       * Holds definition of {@link CalcInfo}
       */
      class AssignmentManager {
        /**
         * Implementation of {@link TimeSheetCalculationInfo}.
         * Extracts all the required data from AllCalcDataManager, AllCalculationData, Time_sheet, etc.
         * Makes copies of all mutable data, unmodifiable lists where possible.
         */
        static class CalcInfo implements TimeSheetCalculationInfo {
          /**
           * Use the passed in AllCalculationData object - DO NOT try to get it from the passed in AllCalcDataManager because
           * the ACD object that's passed in is obtained from AssignmentManager.getAllCalcData method which handles the
           * scenario where a prior period may need to be amended automatically and if ACDM.getAllCalcData() method is called,
           * you might get an unmodifiable ACD
           * @param acdm
           * @param acd
           * @param timeSheetId
           * @param parms
           */
          CalcInfo(AllCalcDataManager acdm, AllCalculationData acd, TimeSheetIdentifier timeSheetId, TimeEntryParms parms) {
            // we'll extract all these vars separately, for readability
            final Right_grp rightGrp = parms.getRight_grp(timeSheetId);
            final AllPolicies ap = acd.getAllPolicies();
            final WDate ppEndForToday = TimeSchedUtil.getPayPeriodRangeForToday(acdm).getEnd();

            this.acd = acd;
            this.timeSheetIdentifier = timeSheetId;
            this.approval_eventList = acd.getApproval_eventList().getUnmodifiableList();
            this.policyProfile = acd.getAsgnmtMaster().getPolicyProfilePolicy(ppEndForToday, ap);
            this.adjustmentsPaidWithTimesheet = createAdjustmentsPaidWithTimesheet(acdm, acd);
            this.bankBalanceResults = acdm.getBankDataForBankBalancePreview(ap, acd, rightGrp.getRight_grp(), acd.getPP_end());
            this.tsoListForPayPreview = Collections.unmodifiableList(acdm.getTsoListForPayPreview(acd, rightGrp.getRight_grp()));
            this.approvedDays = createApprovedDays(acd);

            try {
              // these are fairly involved calculations, and we want to validate the pay period list,
              // so we'll do them both here together instead of upon request
              employeePeriodInfo = acdm.getEmployeePeriodInfo(timeSheetId.getPpEnd());
              payPeriodList = TimeSchedUtil.calcPayPeriodList(acdm, rightGrp.getRight_grp());
            }
            catch (Exception e) {
              throw new InternalApplicationException("Loading calc data for: " + timeSheetId, e);
            }
            assertPayPeriodList();

            AssignmentPeriodStateImpl aps = new AssignmentPeriodStateImpl(employeePeriodInfo,
                rightGrp, parms.getSystemFeature(), ap, ppEndForToday, acdm, policyProfile);
            assignmentPeriodState = aps;
            timeSheetState = new TimeSheetStateImpl(this, aps, policyProfile);
          }

          /**
           * Returns an ApprovalInfo object, which contains information about whether
           * employee/manager approved a timesheet.
           *
           * @see ApprovalInfo
           */
          public ApprovalInfo getApprovalInfo() { return new ApprovalInfo(acd.getTime_sheet(), approval_eventList); }

          public Collection<DbRecTime_sheet_output> getAdjustmentsPaidWithTimesheet() {
            return adjustmentsPaidWithTimesheet;
          }

          public Approval_eventList getApproval_eventList() {
            return approval_eventList;
          }


          /**
           * Returns state information about a given assignment and period, encompassing
           * the cross-section of security related settings calculated from:
           * <ul>
           * <li>App_user Roles (app_user_right and right_grp tables)
           * <li>Assignment effective dates
           * <li>Employee record effective dates
           * <li>System_feature read/write privileges
           * </ul>
           *
           * @return not ever null
           */
          public AssignmentPeriodState getAssignmentPeriodState() {
            return assignmentPeriodState;
          }

          public Set<WDate> getDailyApprovalDays() {
            return approvedDays;
          }

          /**
           * Returns map to the accrual banks data, which is a read only reference
           * of the information contained there. The banks returned will be all banks attached
           * to the assignment, including aggregate banks if applicable.
           *
           * The list will not contain any banks that the current App_user's Right_grp cannot view for this assignment's
           * Policy_profile (as defined by {@link com.workforcesoftware.Gen.Policy.User_entry_rule_detail#getDisplay_bank_set()})
           */
          public List<BankBalanceResults> getBankBalanceResults() {
            return bankBalanceResults;
          }

          public Approval_event getLastValidEmployeeApproveEvent() {
            return approval_eventList.getLastValidEmployeeApproveEvent();
          }

          public Time_sheet_detail_splitList getTime_sheet_detail_splitList() {
            return acd.getTime_sheet().getTime_sheet_detail_splitList().getUnmodifiableList();
          }

          public Time_sheet_detailList getTime_sheet_detailList() {
            return acd.getTime_sheet().getDetailList().getUnmodifiableList();
          }

          public Schedule_detailList getSchedule_detailList() {
            return acd.getSchedule().getDetailList().getUnmodifiableList();
          }

          public Time_sheet_exceptionList getTime_sheet_exceptionList() {
            return acd.getTime_sheet().getTime_sheet_exceptionList().getUnmodifiableList();
          }

          public List<Time_sheet_output> getTsoListForPayPreview() {
            return tsoListForPayPreview;
          }

          public Approval_eventList getValidManagerApproveEvents() {
            return approval_eventList.getValidManagerApproveEvents();
          }

          public TimeSheetIdentifier getTimeSheetIdentifier() {
            return timeSheetIdentifier;
          }

          public EmployeePeriodInfo getEmployeePeriodInfo() {
            return employeePeriodInfo;
          }

          public Employee_master getEmployeeMaster() {
            return new Employee_master(acd.getEmployee_master());
          }

          public Employee getEmployee() {
            return new Employee(acd.getEmployee_master().getEmployeeAsOfOrAfter(getPPEnd()));
          }

          public Asgnmt_master getAsgnmtMaster() {
            return new Asgnmt_master(acd.getAsgnmtMaster());
          }

          public Asgnmt getAsgnmt() {
            return new Asgnmt(acd.getAsgnmt());
          }

          public WDate getPPEnd() {
            return employeePeriodInfo.getPp_end();
          }

          public WDate getPPBegin() {
            return employeePeriodInfo.getPp_begin();
          }

          public Policy_profile getPolicy_profile() {
            return policyProfile;
          }


          /**
           * Return state information about the timesheet, which considers security
           * relationship between the current state (locked/closed/amended) in relation to:
           * <ul>
           * <li>App_user Roles (app_user_right and right_grp tables)
           * <li>Assignment effective dates
           * <li>Employee record effective dates
           * <li>System_feature read/write privileges
           * <li>Approval level of the timesheet
           * </ul>
           *
           * @return not ever null
           */
          public TimeSheetState getTimeSheetState() {
            return timeSheetState;
          }

          public int getVersionNumber() {
            return timeSheetIdentifier.getVersion();
          }

          public PayPeriodData getPayPeriodData() {
            return acd.getPayPeriodData();
          }

          public boolean getIsNewlyUserAmended() {
            return acd.isNewlyUserAmended();
          }

          public List<PayPeriodInfo> getPayPeriodList() {
            return payPeriodList;
          }

          private static Collection<DbRecTime_sheet_output> createAdjustmentsPaidWithTimesheet(AllCalcDataManager acdm, AllCalculationData acd) {
            try { return Collections.unmodifiableCollection(acdm.getAdjustmentsPaidWithTimesheet(acd)); }
            catch (SQLException e) { throw new InternalApplicationException("Unexpected SQL Exception", e); }
          }

          private void assertPayPeriodList() {
            //TODO - probably need to show a "nice" message to the user saying that the employee doesn't
            //TODO - have a viewable/editable pay period.
            if(payPeriodList.isEmpty()) {
              throw new InternalApplicationException("No viewable/editable pay periods found for employee "
                      + employeePeriodInfo.getEmployeeMaster().getEmployee());
            }
          }

          private static Set<WDate> createApprovedDays(AllCalculationData acd) {
            // calculate days approved by daily approvals
            Set<WDate> approvedDays = new HashSet<WDate>();
            for( WDate date : acd.getPayPeriodData().getActiveDates().getAllDates() ) {
              if( acd.isDayApproved(date) ) { approvedDays.add(date); }
            }
            return Collections.unmodifiableSet(approvedDays);
          }

          private final Collection<DbRecTime_sheet_output> adjustmentsPaidWithTimesheet;
          private final Approval_eventList approval_eventList;
          private final AssignmentPeriodState assignmentPeriodState;
          private final List<BankBalanceResults> bankBalanceResults;
          private final List<Time_sheet_output> tsoListForPayPreview;
          private final TimeSheetIdentifier timeSheetIdentifier;
          private final TimeSheetState timeSheetState;
          private final EmployeePeriodInfo employeePeriodInfo;
          private final ArrayList<PayPeriodInfo> payPeriodList;
          private final Set<WDate> approvedDays;
          private final Policy_profile policyProfile;
          private final AllCalculationData acd;
        }

        AssignmentManager(GeneratedId assignmentId, Set<TimeSheetIdentifier> timeSheetIdentifiers, TimeEntryParmsPerAssignment parms) {
          this.asgnmtMaster = getAggregateOrSingleAssignmentMaster(assignmentId);
          setClassFields(parms, timeSheetIdentifiers);
        }

        /**
         * Obtains the single or aggregate assignment master for the given assignmentId.  If the given assignmentId is not a
         * single or aggregate assignment, fetches the aggregate associated with the given assignmentId and returns that.
         *
         * @param assignmentId Id of the assigment to load.
         * @return the single or aggregate assignment master for the given assignmentId.
         */
        private static Asgnmt_master getAggregateOrSingleAssignmentMaster(GeneratedId assignmentId) {
          Asgnmt_master am = Asgnmt_master.load(assignmentId);
          if(am.isAggregate() || am.isSingle()) {
            return am;
          }

          //If it's not a single or aggregate, we need to load the aggregate and load that.
          return Asgnmt_master.load(am.getAggregate_asgnmt());
        }

        AggregateTimeEntryTransactionResults load() {
          return loadImpl();
        }

        /**
         * Reverts any changes that have not been committed to the database on this AssignmentManager
         */
        void revert() {
          updatedAllCalcDataManager = null;
        }

        /**
         * @return Return initial transaction results from originalAllCalcData, which must be set before calling this.
         */
        private AggregateTimeEntryTransactionResults loadImpl() {
          AggregateTimeEntryTransactionResults aggregateResults = (AggregateTimeEntryTransactionResults) parms.getTimeEntryResultsFactory().newInstance();
          for (TimeSheetIdentifier timeSheetId : getTimeSheetIdentifiers()) {
            AllCalculationData acd = getOriginalAllCalcData(timeSheetId);
            // Make sure system_record_id's are assigned - TimeEntryManager and client do not function correctly without
            assignSystemRecordIds(acd);
            CalcInfo calcInfo = new CalcInfo(getOriginalAllCalcDataManager(timeSheetId.getAsgnmt()), acd, timeSheetId, parms);
            //todo-ute-nazim do we really need to perform TimeSheetDiff for the initial load ?
            TimeEntryTransactionResults singleTimeSheetResults =
                    new TimeSheetDiff(timeSheetId, acd, calcInfo, TimeSheetCounters.getEmptyCounters(), NewToOldIdMap.EMPTY,
                                      Collections.EMPTY_LIST, new StatusMapsForTimesheetAndSchedule(), true, parms, null);
            aggregateResults.add(singleTimeSheetResults);
          }
          return aggregateResults;
        }

        /**
         * Assign system_record_id's for time_sheet, time_sheet_detail's and time_sheet_exception's
         * @param acd
         */
        private void assignSystemRecordIds(AllCalculationData acd) {
          assignSystemRecordId(acd.getTime_sheet());
          assignSystemRecordIds(acd.getTime_sheet().getTime_sheet_detailList());
          assignSystemRecordIds(acd.getTime_sheet().getTime_sheet_exceptionList());
        }

        /**
         * todo: store the event.getComment()
         * @param event
         */
        AggregateTimeEntryTransactionResults amend(TransactionApprovalEvent event) {
          TimeSheetIdentifier singleTimeSheetId = event.getTimeSheetIdentifier();
          //TODO: Shouldn't this call createOriginalAllCalcDataManagerIfNeeded?  Seems like we needlessly reload the original
          //TODO: ACDM.  This is because a timesheet which was prepared for amendment couldn't have been modified before it
          //TODO: was amended.
          invalidateAllCalcDataManagers();

          // Create the amended ACD and put into the ACDM cache
          EmployeePeriodInfo epInfo = null;
          try {
            AllCalcDataManager acdm = getOriginalAllCalcDataManager(singleTimeSheetId.getAsgnmt());
            epInfo = acdm.getEmployeePeriodInfo(singleTimeSheetId.getPpEnd());
            AllCalculationData amendedAllCalcData = acdm.createAmendedAllCalcData(epInfo, parms.getApp_user(), WDateTime.now());
            // No need to hook this up to approval change logic, since it is an in-memory change so far.
            //Recalculate the amended ACD object so that timesheet exceptions that are on the closed version (if any)
            // get created on the amended timesheet as well
            amendedAllCalcData.recalc(acdm);
          }
          catch (SQLException e) {
            throw new InternalApplicationException("amending time sheet for " + singleTimeSheetId, e);
          }
          catch (MultipleRowDbRecException e) {
            throw new InternalApplicationException("amending time sheet for " + singleTimeSheetId, e);
          } catch (Exception e) {
            throw new InternalApplicationException("amending time sheet for " + singleTimeSheetId, e);
          }
          return loadImpl();
        }

        TimeSheetTransactionApplier applyTransaction(TimeSheetIdentifier id, TimeEntryTransaction trans) {
          AllCalcDataManager updatedAcdm = getUpdatedAllCalcDataManager(id.getAsgnmt());
          AllCalculationData acd = getAllCalcData(updatedAcdm, id);
          return new TimeSheetTransactionApplier(trans, updatedAcdm, acd, parms, id);
        }

        private void recalc(TimeSheetIdentifier timeSheetId, Approval_event_type approvalEventType) {
          AllCalcDataManager updatedAcdm = getUpdatedAllCalcDataManager(timeSheetId.getAsgnmt());
          AllCalculationData acd = getAllCalcData(updatedAcdm, timeSheetId);
          try {
            if (approvalEventType == Approval_event_type.SAVE_SCHEDULE) {
              // if we're saving a change to the schedule, the timesheet might need to be re-initialized.
              acd.reinitTimeSheet(updatedAcdm, true);
            }
            acd.recalc(updatedAcdm);
          }
          catch (Exception e) {
            throw new InternalApplicationException("calculate for " + timeSheetId, e);
          }
        }

        /**
         * Save one timesheet to a ListWriter using AllCalculationData.
         * @param timeSheetId
         * @param saveEvent
         * @throws Exception
         */
        void save(TimeSheetIdentifier timeSheetId, Approval_event_type saveEvent) throws Exception {
          GeneratedId asgnmtId = timeSheetId.getAsgnmt();
          AllCalcDataManager acdMgr = getUpdatedAllCalcDataManager(asgnmtId);
          AllCalculationData acd = getAllCalcData(acdMgr, timeSheetId);

          // TODO: it'd be nice if we were able to retry a save if it failed because of unrelated changes (to accomodate for
          // concurrent save operations on other component timesheets besides the one that the user is trying to save)
          final ListWriter lw = new ListWriter();
          acd.saveAfterApplyingApprovalEvent(acdMgr, saveEvent, lw);

          //The exceptions must be generated after the ACD has performed approval related operations.
          // Loading the original ACD to get the timesheet object before the approval. This will be used to determine
          // timesheet's approval level before this save event.
          AllCalculationData originalAcd = getAllCalcData(getOriginalAllCalcDataManager(asgnmtId), timeSheetId);
          acdMgr.generateExceptionNotifications(acd, originalAcd.getTime_sheet(), acd.getTime_sheet(), lw);
          lw.writeLists();

        }

        /**
         * If time sheets were saved and written to the database successfully, update internal data
         * to reflect what's in the database as the new "original" data.
         */
        void postSave() {
          originalAllCalcDataManager = updatedAllCalcDataManager;
          updatedAllCalcDataManager = null;
        }

        /**
         * Returns true if the given timesheet contains exceptions that should prevent save.
         * @param timeSheetId time sheet ID
         * @return true if exceptions exist that should prevent save, false otherwise.
         */
        boolean hasExceptionsPreventingSave(TimeSheetIdentifier timeSheetId) {
          AllCalculationData acd = getUpdatedAllCalcData(timeSheetId);
          try {
            return acd.getTime_sheet().getTime_sheet_exceptionList().getDisallow_timesheet_save(acd.getAllPolicies());
          }
          catch (PolicyLookupRequiredException e) {
            throw new InternalApplicationException("Policy error on timesheet " + timeSheetId);
          }
        }

        /**
         * Returns true if the given timesheet contains exceptions that should prevent submit.
         * @param timeSheetId time sheet ID
         * @return true if exceptions exist that should prevent submit, false otherwise.
         */
        boolean hasExceptionsPreventingSubmit(TimeSheetIdentifier timeSheetId) {
          AllCalculationData acd = getUpdatedAllCalcData(timeSheetId);
          try {
            return acd.getTime_sheet().getTime_sheet_exceptionList().getDisallow_timesheet_submit(acd.getAllPolicies());
          }
          catch (PolicyLookupRequiredException e) {
            throw new InternalApplicationException("Policy error on timesheet " + timeSheetId);
          }
        }

        /**
         * Compute {@link TimeEntryTransactionResults} changes to be returned in client.
         * @param timeSheetId
         * @param trans transaction which client just sent
         * @param applyResults results (errors) of applying the transaction
         * @param timeSheetSavedStatus a status to be added representing the save results - SAVED or ERROR, or null if none.
         * @param transactionSuccessful value for {@link TimeEntryTransactionResults#getTransactionWasSuccessful}.
         */
        TimeSheetDiff getDiff(TimeSheetIdentifier timeSheetId, TimeEntryTransaction trans,
                              TimeSheetTransactionApplier applyResults, TransactionStatus timeSheetSavedStatus,
                              boolean transactionSuccessful)
        {
          AllCalcDataManager acdm = getLatestAllCalcDataManager(timeSheetId.getAsgnmt());
          AllCalculationData acd = getAllCalcData(acdm, timeSheetId);
          NewToOldIdMap newToOldIdMap = applyResults.getNewToOldIdMap();
          StatusMapsForTimesheetAndSchedule timeSheetRowStatusMap = applyResults.getTimeEntryRowErrorMap();

          if (cat.isDebugEnabled()) {
            ObjectDumper.debug(cat, "TimeSheetDiff getDiff before", timeSheetRowStatusMap, null, true);
          }

          List<TransactionStatus> timeSheetStatuses = new ArrayList<TransactionStatus>(applyResults.getTimeSheetErrors());
          TimeSheetCounters counters = new TimeSheetCounters(trans, timeSheetId, newToOldIdMap);
          if (timeSheetSavedStatus != null) {
            timeSheetStatuses.add(timeSheetSavedStatus);
            if (timeSheetSavedStatus.getTransaction_status() == Transaction_status.SAVED ||
                    timeSheetSavedStatus.getTransaction_status() == Transaction_status.ERROR) {
              // if we saved or there is an error mark every TimeEntryRow in the transaction as such.
              // get all row id's from the transaction (new id's - not temp id's)
              Collection transTimeSheetRowIds = applyResults.getTransactionTimeSheetRowIds();
              Collection transScheduleRowIds = applyResults.getTransactionScheduleRowIds();
              timeSheetRowStatusMap = new StatusMapsForTimesheetAndSchedule(transTimeSheetRowIds, transScheduleRowIds, timeSheetSavedStatus);
              timeSheetRowStatusMap.addAll(applyResults.getTimeEntryRowErrorMap());
            }
          }

          if (cat.isDebugEnabled()) {
            ObjectDumper.debug(cat, "TimeSheetDiff getDiff after", timeSheetRowStatusMap, null, true);
          }

          CalcInfo calcInfo = new CalcInfo(acdm, acd, timeSheetId, parms);
          return new TimeSheetDiff(timeSheetId, acd, calcInfo, counters, newToOldIdMap, timeSheetStatuses,
                                   timeSheetRowStatusMap, transactionSuccessful, parms, trans);
        }

        TimeSheetCalculationInfo getCalculationInfo(TimeSheetIdentifier timeSheetIdentifier) {
          validate(timeSheetIdentifier);
          final AllCalcDataManager acdm = getLatestAllCalcDataManager(timeSheetIdentifier.getAsgnmt());
          return new CalcInfo(acdm, getAllCalcData(acdm, timeSheetIdentifier), timeSheetIdentifier, parms);
        }

        /**
         * Return the calculation info without auto-amending
         * @param acdm
         * @param timeSheetIdentifier
         * @return
         */
        private TimeSheetCalculationInfo getCalculationInfo(AllCalcDataManager acdm, TimeSheetIdentifier timeSheetIdentifier) {
          validate(timeSheetIdentifier);
          try {
            return new CalcInfo(acdm, acdm.getAllCalcData(timeSheetIdentifier.getPpEnd(), timeSheetIdentifier.getVersion()), timeSheetIdentifier, parms);
          } catch (Exception e) {
            throw new InternalApplicationException("Error creating CalcInfo ", e);\s
          }
        }

        private void invalidateAllCalcDataManagers() {
          originalAllCalcDataManager = null;
          updatedAllCalcDataManager = null;
        }

        private void createUpdatedAllCalcDataManager(Set<TimeSheetIdentifier> timeSheetIds) {
          AllCalcDataManager origAcdMgr = getOriginalAllCalcDataManager(asgnmtMaster.getAsgnmt());
          try {
            WDateList dateList = new WDateList();
            for(TimeSheetIdentifier id : timeSheetIds) {
              dateList.add(id.getPpEnd());
            }
            updatedAllCalcDataManager = origAcdMgr.copyForUpdate(dateList);
          } catch (Exception e) {
            throw new InternalApplicationException("Cloning for " + timeSheetIds, e);
          }
        }

        private void validate(TimeSheetIdentifier timeSheetId) throws IllegalArgumentException {
          if (!timeSheetIdentifiers.contains(timeSheetId)) {
            throw new IllegalArgumentException("Invalid time sheet specified: " + timeSheetId
              + ". not in " + timeSheetIdentifiers);
          }
        }

        /**
         * Obtains the original AllCalcDataManager for the given assignment Id.  The assignment id must be given because in a
         * multiple assignment environment, a specific component needs to be requested, as originalAllCalcDataManager refers
         * to the aggregate assignment.
         *
         * @param asgnmtId The id of the assignment to load.
         * @return the original AllCalcDataManager for the given assignment Id
         */
        private AllCalcDataManager getOriginalAllCalcDataManager(GeneratedId asgnmtId) {
          if(originalAllCalcDataManager == null) {
            originalAllCalcDataManager = createOriginalAllCalcDataManager();
            updatedAllCalcDataManager = null;
          }

          if(asgnmtId.equals(originalAllCalcDataManager.getAsgnmtId())) {
            return originalAllCalcDataManager;
          }

          return originalAllCalcDataManager.getAllCalcDataManager(asgnmtId);
        }

        private AllCalcDataManager createOriginalAllCalcDataManager() {
          AllPolicies allPolicies = PolicyManager.getInstance().getAllPolicies();
          try {
            return AllCalcDataManager.createForAsgnmt(asgnmtMaster, allPolicies);
          }
          catch (Exception e) {
            throw new InternalApplicationException("Failed to load ACDM for assignment with id:"+asgnmtMaster.getAsgnmt(), e);
          }
        }

        /**
         * Obtains the updated AllCalcDataManager for the given assignment Id.  The assignment id must be given because in a
         * multiple assignment environment, a specific component needs to be requested, as originalAllCalcDataManager refers
         * to the aggregate assignment.
         *
         * @param asgnmtId The id of the assignment to load.
         * @return the updated AllCalcDataManager for the given assignment Id
         */
        private AllCalcDataManager getUpdatedAllCalcDataManager(GeneratedId asgnmtId) {
          if(updatedAllCalcDataManager == null) {
            throw new InternalApplicationException("Attempted to use the updated all calc data manager before it was created.");
          }

          if(asgnmtId.equals(updatedAllCalcDataManager.getAsgnmtId())) {
            return updatedAllCalcDataManager;
          }

          return updatedAllCalcDataManager.getAllCalcDataManager(asgnmtId);
        }

        /**
         * Obtains the updated AllCalcDataManager for the given assignment Id, if present.  If not present, returns the
         * original ACDM instead.  The assignment id must be given because in a
         * multiple assignment environment, a specific component needs to be requested, as originalAllCalcDataManager refers
         * to the aggregate assignment.
         *
         * @param asgnmtId The id of the assignment to load.
         * @return  he updated AllCalcDataManager for the given assignment Id, if present.  If not present, returns the
         * original ACDM instead.
         */
        AllCalcDataManager getLatestAllCalcDataManager(GeneratedId asgnmtId) {
          if (updatedAllCalcDataManager != null) {
            return getUpdatedAllCalcDataManager(asgnmtId);
          }

          return getOriginalAllCalcDataManager(asgnmtId);
        }

        //todo-ute: the method name is misleading as it may also create a new amended ACD if period is amendable by system on user's behalf
        private AllCalculationData getAllCalcData(AllCalcDataManager mgr, TimeSheetIdentifier id) {
          try {
            EmployeePeriodInfo epInfo = mgr.getEmployeePeriodInfo(id.getPpEnd());
            if (epInfo == null) {
              final PolicyID asgnmtPolicyProfile = mgr.getAsgnmtMaster().getPolicy_profile(id.getPpEnd());
              final PolicyID policyProfileId = (asgnmtPolicyProfile == null) ? PolicyID.EMPTY_POLICYID : asgnmtPolicyProfile;
              throw new InvalidPayPeriodException("EmployeePeriodInfo could not be obtained for employee " +
                  id.getEmployee() + " and assignment " + id.getAsgnmt() + " and period end - " + id.getPpEnd() + ". " +
                  "Possibly because the employee is not active on that date or there are not enough initialized policy profile " +
                  "periods for policy profile " + policyProfileId );
            }

            TimeSheetCalculationInfo calcInfo = this.getCalculationInfo(mgr, id);
            AssignmentPeriodState asgnmtPeriodState = calcInfo.getAssignmentPeriodState();
            TimeSheetState timesheetState = calcInfo.getTimeSheetState();
            //If requesting EDIT_VERSION, check to see if one exists or that period is amendable by the logged in user
            if (id.getVersion() == EmployeePeriodVersionInfo.EDIT_VERSION && !epInfo.hasEditVersion()
                && !isPeriodAmendable(epInfo, mgr, asgnmtPeriodState, timesheetState) ) {
              throw new InternalApplicationException("An editable timesheet could not be obtained for employee " +
                  id.getEmployee() + " and period end - " + id.getPpEnd());
            }

            //Check to see if an editable ACD is in ACD manager's cache - if one exists, use it
            //An empty unmodifiable ACD for a prior period might be in cache if an ACD for prior timesheet is requested and
            //if that's the case, we want to still be able to create an amended ACD
            final AllCalculationData acd = mgr.getAllCalcData(id.getPpEnd(),id.getVersion(),AllCalculationData.FETCH_CACHED_ONLY);
            if (acd != null && !acd.isUnmodifiable()) {
              return acd;
            }

            //Create an amended ACD only if the period is amendable and the logged in user can amend it
            if (isAmendableBySystemForUser(epInfo, mgr, asgnmtPeriodState)) {
              final AllCalculationData amendedAllCalcData = mgr.createAmendedAllCalcData(epInfo, parms.getApp_user(), WDateTime.now());
              // No need to hook this up to approval change logic, since it is an in-memory change so far.
              //Recalculate the amended ACD so that timesheet exceptions (if any) get created on the amended timesheet
              amendedAllCalcData.recalc(mgr);
            }

            //By this time, the ACD mgr's ACD cache will have the amended ACD if one's created in this method
            //and we can retrieve the cached amended ACD or the ACD from the database
            return mgr.getAllCalcData(id.getPpEnd(), id.getVersion());
          } catch(InvalidPayPeriodException e) {
            //Rethrow InvalidPeriodExceptions--don't wrap them.  We want them to be typed as InvalidPeriodExceptions.
            throw e;
          } catch (Exception e) {
            throw new InternalApplicationException("Timesheet could not be obtained for employee " +
                  id.getEmployee() + ", assignment " + id.getAsgnmt()+ ", period end " + id.getPpEnd() +  " and version " + id.getVersion() + ".", e);
          }
        }

        /**
         * A period is amendable if one of the following is true:
         * <pre>
         * - {@link #isAmendableBySystemForUser(EmployeePeriodInfo, AllCalcDataManager, AssignmentPeriodState)
         *    amendable by system on users's behalf}
         * - {@link TimeSheetState#isAmendable() amendable directly by user} generally by clicking "Amend" button on
         *   prior active period with one or more closed timesheets
         * </pre>
         */
        private static boolean isPeriodAmendable(EmployeePeriodInfo epInfo, AllCalcDataManager mgr,
                                                 AssignmentPeriodState asgnmtPeriodState, TimeSheetState timesheetState)
                throws SQLException, MultipleRowDbRecException {
          return timesheetState.isAmendable() || isAmendableBySystemForUser(epInfo, mgr, asgnmtPeriodState);
        }

        /**
         * A period is amendable by system on user's behalf (not the same as AUTO_AMEND or SYS_AMEND which are also created by
         * system) if user has amend rights for the specified period as of today and if specified period is a priod period
         * with no timesheet or it's a prior period timesheet with just system amended but not user amended
         * @param epInfo
         * @param mgr
         * @param asgnmtPeriodState
         * @return
         * @throws PolicyLookupException
         * @throws MultipleRowDbRecException
         * @throws SQLException
         * @see AssignmentPeriodState#isPeriodAmendable()\s
         * @see Approval_event_type#SYS_AMEND
         * @see Approval_event_type#AMEND
         */
        private static boolean isAmendableBySystemForUser(EmployeePeriodInfo epInfo, AllCalcDataManager mgr,
                                                          AssignmentPeriodState asgnmtPeriodState)
            throws MultipleRowDbRecException, SQLException {
          return asgnmtPeriodState.isPeriodAmendable() &&  // TODO: this doesn't match the javadoc?
              ( mgr.isPriorModifiablePeriodWithNoTimesheet(epInfo) || epInfo.isPriorPeriodWithSysAmendVersionOnly() );
        }

        private AllCalculationData getOriginalAllCalcData(TimeSheetIdentifier timeSheetIdentifier) {
          return getAllCalcData(getOriginalAllCalcDataManager(timeSheetIdentifier.getAsgnmt()), timeSheetIdentifier);
        }

        private AllCalculationData getUpdatedAllCalcData(TimeSheetIdentifier timeSheetIdentifier) {
          return getAllCalcData(getUpdatedAllCalcDataManager(timeSheetIdentifier.getAsgnmt()), timeSheetIdentifier);
        }


        private Set<TimeSheetIdentifier> getTimeSheetIdentifiers() {
          return timeSheetIdentifiers;
        }

        /**
         * Returns the pay period end for the period containing date provided, relative
         * to the defined periods for this assignment.
         *
         * @param aDateInUnknownPeriod any date we want to know the period for
         * @return never null
         */
        WDate getPpEndForDate(GeneratedId asgnmtId, WDate aDateInUnknownPeriod) {
          return TimeSchedUtils.getPpEndForDate(getOriginalAllCalcDataManager(asgnmtId), aDateInUnknownPeriod);
        }

        /**
         * Assign system_record_id's to any DbRec objecs in the collection which lack them.
         * @param dbRecList List of DbRec objects on which to check the system_record_id.  Records in list will be modified.
         */
        static private void assignSystemRecordIds(ListWrapBase dbRecList) {
          for (Iterator iterator = dbRecList.getCollection().iterator(); iterator.hasNext();) {
            DbRec dbRec = (DbRec) iterator.next();
            assignSystemRecordId(dbRec);
          }
        }

        /**
         * Assign system_record_id to a DbRec object, if it is not set or contains a temporary id.
         * @param dbRec DbRec objects on which to check/change the system_record_id.
         */
        static private void assignSystemRecordId(DbRec dbRec) {
          if (dbRec.getSystem_record_id() == null || dbRec.getSystem_record_id().requiresPermanentId()) {
            dbRec.setSystem_record_id(SystemId.getNewID());
          }
        }

        /**
         * Removes the last approval event (employee approval) from approval event list from the AllCalculationData object of
         * {@link #updatedAllCalcDataManager}
         * @param timeSheetId  - Used to get the {@link AllCalculationData} object from {@link #updatedAllCalcDataManager}
         */
        void undoEmployeeApproval(TimeSheetIdentifier timeSheetId) {
          final AllCalculationData updatedAllCalcData = getUpdatedAllCalcData(timeSheetId);
          updatedAllCalcData.getApproval_eventList().removeLastApprovalEvent();
        }

        /**
         * Update the current object to be able to be used for other timesheets for this assignment
         * @param parms
         * @param timeSheetIdentifiers
         */
        public void update(TimeEntryParmsPerAssignment parms, Set<TimeSheetIdentifier> timeSheetIdentifiers) {
          setClassFields(parms, timeSheetIdentifiers);
        }

        private void setClassFields(TimeEntryParmsPerAssignment parms, Set<TimeSheetIdentifier> timeSheetIdentifiers) {
          this.parms = parms;
          this.timeSheetIdentifiers = TimeEntryParmsPerAssignment.TIMESHEET_IDENTIFIER_TREESET_FACTORY.newInstance();
          this.timeSheetIdentifiers.addAll(timeSheetIdentifiers);
        }

        /**
         * Recalculates and Saves timesheets (if saveData == true) for timesheet ids in {@link #parms} that belong to this
         * assignment
         *
         * @param trans
         * @return AggregateTimeEntryTransactionResults for all saved timesheets
         * @throws Exception in case of fatal error during timesheet recalc & save e.g. errors while store data in DB etc.
         * This precludes ConcurrentUserModification exception. Any exception thrown from this method, eventually gets
         * escalated as transaction exception
         */
        public TimeEntryTransactionResults recalcAndSaveTimesheets(TimeEntryTransaction trans, boolean saveData) throws Exception {
          // TODO-13285: Remove this code and implement merging concurrently modified time sheets
          AllCalcDataManager tempAcdMgr = null;
          AggregateTimeEntryTransactionResults results = (AggregateTimeEntryTransactionResults) parms.getTimeEntryResultsFactory().newInstance();
          Set<TimeSheetIdentifier> timeSheetIds = getAffectedTimeSheetIdentifiers(trans, parms);
          int retryCount = 0;
          boolean canRetryConcurrentModification = true;
          if (timeSheetIds.isEmpty()) {
            return results;
          }

          assert timeSheetIdentifiers.containsAll(timeSheetIds) : "Attempted to recalculate a timesheet identifier not managed by this AssignmentManager.";
          createUpdatedAllCalcDataManager(timeSheetIds);

          TransactionStatus timeSheetSaveStatus = null;
          Map<TimeSheetIdentifier, TimeSheetTransactionApplier> applyResultsMap = new HashMap<TimeSheetIdentifier, TimeSheetTransactionApplier>();
          for (TimeSheetIdentifier timeSheetId : timeSheetIds) {
            TimeSheetTransactionApplier applyResults = applyTransaction(timeSheetId, trans);
            if(applyResults.getApprovalEventType() != Approval_event_type.SAVE_SCHEDULE) {
              applyResultsMap.put(timeSheetId, applyResults);
            }

            recalc(timeSheetId, applyResults.getApprovalEventType());

            boolean isWithdrawal =
                    (trans.findTransactionApprovalEvent(timeSheetId, Approval_event_type.WITHDRAWAL) != null);
            if (!isWithdrawal) {
              // There are 3 conditions identified in the following if statement that can prevent an "action" on the
              // current timesheet. The first is an exception that prevents save. In this case, we should never allow a
              // save of the timesheet to occur. The second condition is an exception that prevents submit. In this
              // condition, we should only allow a save if the user is not attempting to submit (apply an APPROVAL event).
              // The third case is errors that occur while applying transactions to the timesheet. An example of when this
              // may occur is if the same time sheet detail row is modified by 2 users. In all of these cases we prevent
              // the save operation from executing.
              // In the next condition where we check if an error has occurred, we remove the last approval event from the
              // timesheet if we did not save AND it was a submit (APPROVAL) event. Transactions only allow 1 event to be applied
              // to the timesheet. The transaction explicitly excludes SAVE_TIME_SHEET and SAVE_SCHEDULE events from being applied.
              // It relies on these events being applied only by the save operation itself. As a result, a maximum of 2 events may
              // be added to the timesheet as a result of the transaction (the event on the transaction if it was not a save event)
              // and the SAVE_TIME_SHEET or SAVE_SCHEDULE event added during the save operation.

              if (hasSubmitEvent(trans, timeSheetId) &&
                      hasExceptionsPreventingSubmit(timeSheetId)) {
                timeSheetSaveStatus = getHighestPriorityError(timeSheetSaveStatus, Messages.EXCEPTIONS_PREVENT_SUBMIT);
              }

              if (hasExceptionsPreventingSave(timeSheetId)) {
                timeSheetSaveStatus = new TransactionStatusImpl(Transaction_status.ERROR,
                        Messages.EXCEPTIONS_PREVENT_SAVE.getLabel());
              }
            }

            if (applyResults.hasErrors()) {
              timeSheetSaveStatus = new TransactionStatusImpl(Transaction_status.ERROR,
                        Messages.TIME_SHEET_EXCEPTION_JAVA_EXCEPTION .getLabel());
            }
          }


          boolean errorPreventsAction = (timeSheetSaveStatus != null);


          Approval_event lastOriginalApprovalEvent = null;
          for (TimeSheetIdentifier timeSheetId : timeSheetIds) {
            TimeSheetTransactionApplier applyResults = applyResultsMap.get(timeSheetId);
            try {
              while (saveData && canRetryConcurrentModification) {
                if (errorPreventsAction) {
                  // If the changes were not saved due to errors, and this operation (transaction) was an employee timesheet submission,
                  // remove the approval event from the unsaved ACD.
                  // NOTE: We do not have to concern ourselves with removing SAVE_TIME_SHEET or SAVE_SCHEDULE events here as these events
                  // are explicitly EXCLUDED in the TimeSheetTransactionApplier (not applied) and are applied by the save operation (which
                  // has not executed if we made it here.
                  if (hasSubmitEvent(trans, timeSheetId)) {
                    undoEmployeeApproval(timeSheetId);
                  }
                } else {
                  save(timeSheetId, applyResults.getApprovalEventType());
                  timeSheetSaveStatus = new TransactionStatusImpl(Transaction_status.SAVED,
                          Messages.TIME_SHEET_SAVED.getLabel());
                  postSave();
                }
                // All operations completed, so retries are no longer necessary
                canRetryConcurrentModification = false;
              }
            } catch (ConcurrentUserModificationException ex) {
              // Store the old AcdMgr, when a concurrent mod error due to another user modifying a time sheet occurs we want
              // to receive the same new approval events to ensure that the user is forced to reload the time sheet to continue.
              // TODO-13285: Remove the tempAcdMgr and make merging concurrently modified time sheets possible,
              // TODO-13285: requires considering multiple edge cases for the user interface
              if (tempAcdMgr == null) {
                tempAcdMgr = getOriginalAllCalcDataManager(asgnmtMaster.getAsgnmt());
              }

              if (retryCount == MAX_RETRIES) {
                // return the acdMgr to its previous state, this ensures the user must reload the time sheet
                // TODO-13285: Remove this code and implement merging concurrently modified time sheets
                originalAllCalcDataManager = tempAcdMgr;
                GeneratedId errorId = ServerErrorLogger.singleton.log(new ServerError("Exception saving time sheet", ex,
                        Program_source.SERVER_REQUEST, parms.getApp_user().getLogin_id()));
                cat.error("Unable to save time sheet.  debug_error_log id:" + errorId, ex);
                //Use timeSheetSaveStatus to send concurrent error message to user.
                errorPreventsAction = true;
                timeSheetSaveStatus = new TransactionStatusImpl(Transaction_status.ERROR,
                        Messages.TIME_SHEET_EXCEPTION_CONCURRENT_USER.getLabel());
                // We have retried saving and have hit the MAX_RETRIES threshold, so stop retrying.
                canRetryConcurrentModification = false;
              } else {
                // On the first retry, we need to capture the last (most recent) approval event that existed on this timesheet prior to applying
                // any transactions because when we reload the "original" ACD to attempt to reapply the transactions we will lose track
                // of this (we'll pull in approval events for transactions that were saved in other sessions/processes). This is necessary to
                // allow more than 1 retry
                if (lastOriginalApprovalEvent == null) {
                  lastOriginalApprovalEvent = getAllCalcData(getOriginalAllCalcDataManager(timeSheetId.getAsgnmt()), timeSheetId).getApproval_eventList().getLastApprovalEvent();
                }
                retryCount++;
                // attempt to reapply the transaction
                try {
                  applyResults = attemptTimeSheetTransactionReapply(trans, timeSheetId, lastOriginalApprovalEvent);
                } catch (ConcurrentUserModificationException cume) {
                  // return the acdMgr to its previous state, this ensures the user must reload the time sheet
                  // TODO-13285: Remove this code and implement merging concurrently modified time sheets
                  originalAllCalcDataManager = tempAcdMgr;
                  // if we were unable to reapply transactions, no further retries are necessary (because it will just keep failing)
                  // subsequent retries only succeed if we were able to reapply the new transactions successfully and still received
                  // another ConcurrentUserModificationException (a second change occurred while applying transactions)
                  canRetryConcurrentModification = false;
                  errorPreventsAction = true;
                  timeSheetSaveStatus = new TransactionStatusImpl(Transaction_status.ERROR,
                          Messages.TIME_SHEET_EXCEPTION_CONCURRENT_USER.getLabel());
                }
              }
            }
            results.add(getDiff(timeSheetId, trans, applyResults, timeSheetSaveStatus, !errorPreventsAction));
          }
          return results;
        }

        """;

    @NonNls String part2 = """

        private TransactionStatus getHighestPriorityError(TransactionStatus existingStatus, Message newError) {
          if(existingStatus == null) {
            return newError;
          }
          TransactionStatus timeSheetSaveStatus = new TransactionStatusImpl(Transaction_status.ERROR,
                  );
          return timeSheetSaveStatus;
        }

        /**
         * Returns true if the given TimeEntryTransaction has a submit event for the given time sheet.
         * @param trans TimeEntryTransaction to search
         * @param timeSheetId time sheet to search
         * @return true if a submit event exists
         */
        private static boolean hasSubmitEvent(TimeEntryTransaction trans, TimeSheetIdentifier timeSheetId) {
          return trans.findTransactionApprovalEvent(timeSheetId, Approval_event_type.APPROVAL) != null;
        }

        /**
         * Resolves modifications that impact only non-"input" values on the timesheet to prevent them
         * from causing ConcurrentUserModificationExceptions on subsequent attempts at saving the data
         * @param trans time entry transaction to resolve
         * @param id time sheet ID
         * @param lastApprovalEvent the most recent approval event on the timesheet prior to any transactions
         * @return TimeSheetTransactionApplier if transactions were reapplied
         * @throws Exception on error loading the acd
         */
        private TimeSheetTransactionApplier attemptTimeSheetTransactionReapply(final TimeEntryTransaction trans, final TimeSheetIdentifier id, Approval_event lastApprovalEvent) throws Exception {
          // Clean out the ACDM cache to force reloads of the data from the database
          invalidateAllCalcDataManagers();
          AllCalculationData originalAcd = getAllCalcData(getOriginalAllCalcDataManager(id.getAsgnmt()), id);
          // Get any new approval events that exist in the database
          Approval_eventList newApprovalEvents = originalAcd.getApproval_eventList().getApprovalsSince(lastApprovalEvent);
          // Check if a "concurrent save" is possible given the events that have occurred
          if (newApprovalEvents.canConcurrentSave()) {
            //TODO:FIX
            //createUpdatedAllCalcDataManager(listOfAllIdsHere);
            TimeSheetTransactionApplier applyResults = applyTransaction(id, trans); // reapply transactions
            recalc(id, applyResults.getApprovalEventType()); // recalculate
            DbRecTime_sheet originalTimeSheet = originalAcd.getTime_sheet();
            DbRecTime_sheet updatedTimeSheet = getAllCalcData(getUpdatedAllCalcDataManager(id.getAsgnmt()), id).getTime_sheet();
            // Copy the system fields from the original time sheet to the updated time sheet because
            // we have determined at this point that the concurrent save is okay based on the difference
            // between these 2 time sheets. Copying these fields over will allow the save to complete
            // without causing a ConcurrentUserModificationException by making the update counter match.
            DbRecFieldCopier copier = new DbRecFieldCopier(DataDictionary.getCltnOfSystemFieldNames());
            copier.copyFields(originalTimeSheet, updatedTimeSheet);
            return applyResults;
          } else {
            // Otherwise, throw the exception
            throw new ConcurrentUserModificationException("Unable to save time sheet for assignment " + originalAcd.getAsgnmtId() + "due to concurrent changes.");
          }
        }

        /**
         * Returns a set of AssignmentManagers that are affected by changes in the given transaction and managed by this
         * TimeEntryManager.
         *
         * @return a set of AssignmentManagers that are affected by changes in the given transaction and managed by this
         * TimeEntryManager.
         */
        public Set<TimeSheetIdentifier> getAffectedTimeSheetIdentifiers(TimeEntryTransaction transaction, TimeEntryParmsPerAssignment parms) {
          Set<TimeSheetIdentifier> affectedTimeSheetIds = new HashSet<TimeSheetIdentifier>();
          for(GeneratedId assignmentId : getAssignmentIds()) {
            for(TimeSheetIdentifier timeSheetId : parms.getTimeSheetIdentifiersForAssignment(assignmentId)) {
              if(!transaction.obtainAllRows(timeSheetId).isEmpty()) {
                affectedTimeSheetIds.add(timeSheetId);
              }
            }
          }
          return affectedTimeSheetIds;
        }

        /**
         * Obtains all assignment id's that are managed by this AssignmentManager.
         *
         * @return all assignment id's that are managed by this AssignmentManager.
         */
        public Set<GeneratedId> getAssignmentIds() {
          Set<GeneratedId> assignmentIds = new HashSet<GeneratedId>();
          Asgnmt_masterList amList = asgnmtMaster.getCompAsgnmtMasters();
          for(Asgnmt_master am : amList) {
            assignmentIds.add(am.getAsgnmt());
          }
          return assignmentIds;
        }

        public GeneratedId getEmployee() {
          return asgnmtMaster.getEmployee();
        }

        /** Single or aggregate assignment master associated with this manager */
        private final Asgnmt_master asgnmtMaster;

        private TimeEntryParmsPerAssignment parms;

        /** Set of {@link TimeSheetIdentifier}'s which are managed by this object.
         * Composed of all the identifiers in the TimeEntryParms for this assignment and related components.
         */
        private Set<TimeSheetIdentifier> timeSheetIdentifiers;

        /**
         * Original Single or Aggregate ACDM, as loaded from the database.  ACD's in here are not modified.
         * This ACDM is lazily loaded, and should always be obtained from {@link #getOriginalAllCalcDataManager(GeneratedId)}.
         *
         * Null means that the ACDM has not yet been loaded from the database, and any attempts to use it should first
         * initialize it.  Note:  The meaning of null is different than updatedAllCalcDataManager's null meaning.
         */
        private AllCalcDataManager originalAllCalcDataManager = null;

        /**
         * Updated Single or Aggregate ACDM--a shallow copy of originalAllCalcDataManager.  ACD's in here are modified by
         * TimeEntryTransactions.  This ACDM is NOT lazily loaded--null means no changes present, and non-null means the
         * updated ACDM has been explicitly created.
         *
         * Null means that no changes have been made to the ACDM or data, and any attempts to use it should either throw a
         * descriptive error, or explicitly create the updatedAllCalcDataManager.
         * Note:  The meaning of null is different than originalAllCalcDataManager's null meaning.
         */
        private AllCalcDataManager updatedAllCalcDataManager = null;

        private static final int MAX_RETRIES = 2;

        private static final Category cat=Category.getInstance(AssignmentManager.class.getName());
      }""";
    configureFromFileText("Foo.java", part1 + part2);

    final PsiDocumentManager docManager = PsiDocumentManager.getInstance(getProject());
    final Document doc = docManager.getDocument(getFile());
    WriteCommandAction.runWriteCommandAction(getProject(), () -> doc.insertString(part1.length(), "/**"));

    DebugUtil.runWithCheckInternalInvariantsEnabled(() -> {
      docManager.commitAllDocuments();
    });
  }
}
