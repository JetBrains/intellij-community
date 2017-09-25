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
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

public class TreeIsCorrectAfterDiffReparseTest extends LightCodeInsightTestCase {

  public void testIDEADEV41862() {
    @NonNls String part1 = "package com.test;\n" +
                   "\n" +
                   "\n" +
                   "//------------------------------------------------------------------\n" +
                   "// Copyright (c) 1999, 2007\n" +
                   "// WorkForce Software, Inc.\n" +
                   "// All rights reserved.\n" +
                   "//\n" +
                   "// Web-site: http://www.workforcesoftware.com\n" +
                   "// E-mail:   support@workforcesoftware.com\n" +
                   "// Phone:    (877) 493-6723\n" +
                   "//\n" +
                   "// This program is protected by copyright laws and is considered\n" +
                   "// a trade secret of WorkForce Software.  Access to this program\n" +
                   "// and source code is granted only to licensed customers.  Under\n" +
                   "// no circumstances may this software or source code be distributed\n" +
                   "// without the prior written consent of WorkForce Software.\n" +
                   "// -----------------------------------------------------------------\n" +
                   "\n" +
                   "import com.workforcesoftware.Data.Employee.*;\n" +
                   "import com.workforcesoftware.Data.*;\n" +
                   "import com.workforcesoftware.Data.assignment.Asgnmt_master;\n" +
                   "import com.workforcesoftware.Data.assignment.Asgnmt;\n" +
                   "import com.workforcesoftware.Data.Output.Time_sheet_output;\n" +
                   "import com.workforcesoftware.Data.TimeSched.PayPeriodData;\n" +
                   "import com.workforcesoftware.Data.TimeSched.TimeSchedUtils;\n" +
                   "import com.workforcesoftware.Data.TimeSched.Schedule.Schedule_detailList;\n" +
                   "import com.workforcesoftware.Data.TimeSched.TimeSheet.*;\n" +
                   "import com.workforcesoftware.Data.assignment.Asgnmt_masterList;\n" +
                   "import com.workforcesoftware.Exceptions.*;\n" +
                   "import com.workforcesoftware.Gen.Choice.Approval_event_type;\n" +
                   "import com.workforcesoftware.Gen.Choice.Transaction_status;\n" +
                   "import com.workforcesoftware.Gen.Choice.Messages;\n" +
                   "import com.workforcesoftware.Gen.Choice.Program_source;\n" +
                   "import com.workforcesoftware.Gen.Other.DbRec.DbRecTime_sheet_output;\n" +
                   "import com.workforcesoftware.Gen.Other.DbRec.DbRecTime_sheet;\n" +
                   "import com.workforcesoftware.Gen.Other.List.WDateList;\n" +
                   "import com.workforcesoftware.Gen.Policy.Right_grp;\n" +
                   "import com.workforcesoftware.Gen.Policy.Policy_profile;\n" +
                   "import com.workforcesoftware.Policy.*;\n" +
                   "import com.workforcesoftware.Util.DateTime.WDate;\n" +
                   "import com.workforcesoftware.Util.DateTime.WDateTime;\n" +
                   "import com.workforcesoftware.Util.DB.ListWriter;\n" +
                   "import com.workforcesoftware.Util.DB.DbRecFieldCopier;\n" +
                   "import com.workforcesoftware.ClientRequests.TimeSched.TimeSchedUtil;\n" +
                   "import com.workforcesoftware.ClientRequests.TimeSched.PayPeriodInfo;\n" +
                   "import com.workforcesoftware.ClientRequests.TimeEntry.ApprovalInfo;\n" +
                   "import com.workforcesoftware.ClientRequests.TimeEntry.BankBalanceResults;\n" +
                   "import com.workforcesoftware.Misc.ServerErrorLogger;\n" +
                   "import com.workforcesoftware.Misc.ServerError;\n" +
                   "import com.workforcesoftware.AssignmentPeriod.*;\n" +
                   "import com.workforcesoftware.AssignmentPeriod.TimeSheetState;\n" +
                   "import com.workforcesoftware.Dictionary.DataDictionary;\n" +
                   "\n" +
                   "import java.sql.SQLException;\n" +
                   "import java.util.*;\n" +
                   "import org.log4j.Category;\n" +
                   "\n" +
                   "/**\n" +
                   " * Holds definition of {@link CalcInfo}\n" +
                   " */\n" +
                   "class AssignmentManager {\n" +
                   "  /**\n" +
                   "   * Implementation of {@link TimeSheetCalculationInfo}.\n" +
                   "   * Extracts all the required data from AllCalcDataManager, AllCalculationData, Time_sheet, etc.\n" +
                   "   * Makes copies of all mutable data, unmodifiable lists where possible.\n" +
                   "   */\n" +
                   "  static class CalcInfo implements TimeSheetCalculationInfo {\n" +
                   "    /**\n" +
                   "     * Use the passed in AllCalculationData object - DO NOT try to get it from the passed in AllCalcDataManager because\n" +
                   "     * the ACD object that's passed in is obtained from AssignmentManager.getAllCalcData method which handles the\n" +
                   "     * scenario where a prior period may need to be amended automatically and if ACDM.getAllCalcData() method is called,\n" +
                   "     * you might get an unmodifiable ACD\n" +
                   "     * @param acdm\n" +
                   "     * @param acd\n" +
                   "     * @param timeSheetId\n" +
                   "     * @param parms\n" +
                   "     */\n" +
                   "    CalcInfo(AllCalcDataManager acdm, AllCalculationData acd, TimeSheetIdentifier timeSheetId, TimeEntryParms parms) {\n" +
                   "      // we'll extract all these vars separately, for readability\n" +
                   "      final Right_grp rightGrp = parms.getRight_grp(timeSheetId);\n" +
                   "      final AllPolicies ap = acd.getAllPolicies();\n" +
                   "      final WDate ppEndForToday = TimeSchedUtil.getPayPeriodRangeForToday(acdm).getEnd();\n" +
                   "\n" +
                   "      this.acd = acd;\n" +
                   "      this.timeSheetIdentifier = timeSheetId;\n" +
                   "      this.approval_eventList = acd.getApproval_eventList().getUnmodifiableList();\n" +
                   "      this.policyProfile = acd.getAsgnmtMaster().getPolicyProfilePolicy(ppEndForToday, ap);\n" +
                   "      this.adjustmentsPaidWithTimesheet = createAdjustmentsPaidWithTimesheet(acdm, acd);\n" +
                   "      this.bankBalanceResults = acdm.getBankDataForBankBalancePreview(ap, acd, rightGrp.getRight_grp(), acd.getPP_end());\n" +
                   "      this.tsoListForPayPreview = Collections.unmodifiableList(acdm.getTsoListForPayPreview(acd, rightGrp.getRight_grp()));\n" +
                   "      this.approvedDays = createApprovedDays(acd);\n" +
                   "\n" +
                   "      try {\n" +
                   "        // these are fairly involved calculations, and we want to validate the pay period list,\n" +
                   "        // so we'll do them both here together instead of upon request\n" +
                   "        employeePeriodInfo = acdm.getEmployeePeriodInfo(timeSheetId.getPpEnd());\n" +
                   "        payPeriodList = TimeSchedUtil.calcPayPeriodList(acdm, rightGrp.getRight_grp());\n" +
                   "      }\n" +
                   "      catch (Exception e) {\n" +
                   "        throw new InternalApplicationException(\"Loading calc data for: \" + timeSheetId, e);\n" +
                   "      }\n" +
                   "      assertPayPeriodList();\n" +
                   "\n" +
                   "      AssignmentPeriodStateImpl aps = new AssignmentPeriodStateImpl(employeePeriodInfo,\n" +
                   "          rightGrp, parms.getSystemFeature(), ap, ppEndForToday, acdm, policyProfile);\n" +
                   "      assignmentPeriodState = aps;\n" +
                   "      timeSheetState = new TimeSheetStateImpl(this, aps, policyProfile);\n" +
                   "    }\n" +
                   "\n" +
                   "    /**\n" +
                   "     * Returns an ApprovalInfo object, which contains information about whether\n" +
                   "     * employee/manager approved a timesheet.\n" +
                   "     *\n" +
                   "     * @see ApprovalInfo\n" +
                   "     */\n" +
                   "    public ApprovalInfo getApprovalInfo() { return new ApprovalInfo(acd.getTime_sheet(), approval_eventList); }\n" +
                   "\n" +
                   "    public Collection<DbRecTime_sheet_output> getAdjustmentsPaidWithTimesheet() {\n" +
                   "      return adjustmentsPaidWithTimesheet;\n" +
                   "    }\n" +
                   "\n" +
                   "    public Approval_eventList getApproval_eventList() {\n" +
                   "      return approval_eventList;\n" +
                   "    }\n" +
                   "\n" +
                   "\n" +
                   "    /**\n" +
                   "     * Returns state information about a given assignment and period, encompassing\n" +
                   "     * the cross-section of security related settings calculated from:\n" +
                   "     * <ul>\n" +
                   "     * <li>App_user Roles (app_user_right and right_grp tables)\n" +
                   "     * <li>Assignment effective dates\n" +
                   "     * <li>Employee record effective dates\n" +
                   "     * <li>System_feature read/write privileges\n" +
                   "     * </ul>\n" +
                   "     *\n" +
                   "     * @return not ever null\n" +
                   "     */\n" +
                   "    public AssignmentPeriodState getAssignmentPeriodState() {\n" +
                   "      return assignmentPeriodState;\n" +
                   "    }\n" +
                   "\n" +
                   "    public Set<WDate> getDailyApprovalDays() {\n" +
                   "      return approvedDays;\n" +
                   "    }\n" +
                   "\n" +
                   "    /**\n" +
                   "     * Returns map to the accrual banks data, which is a read only reference\n" +
                   "     * of the information contained there. The banks returned will be all banks attached\n" +
                   "     * to the assignment, including aggregate banks if applicable.\n" +
                   "     *\n" +
                   "     * The list will not contain any banks that the current App_user's Right_grp cannot view for this assignment's\n" +
                   "     * Policy_profile (as defined by {@link com.workforcesoftware.Gen.Policy.User_entry_rule_detail#getDisplay_bank_set()})\n" +
                   "     */\n" +
                   "    public List<BankBalanceResults> getBankBalanceResults() {\n" +
                   "      return bankBalanceResults;\n" +
                   "    }\n" +
                   "\n" +
                   "    public Approval_event getLastValidEmployeeApproveEvent() {\n" +
                   "      return approval_eventList.getLastValidEmployeeApproveEvent();\n" +
                   "    }\n" +
                   "\n" +
                   "    public Time_sheet_detail_splitList getTime_sheet_detail_splitList() {\n" +
                   "      return acd.getTime_sheet().getTime_sheet_detail_splitList().getUnmodifiableList();\n" +
                   "    }\n" +
                   "\n" +
                   "    public Time_sheet_detailList getTime_sheet_detailList() {\n" +
                   "      return acd.getTime_sheet().getDetailList().getUnmodifiableList();\n" +
                   "    }\n" +
                   "\n" +
                   "    public Schedule_detailList getSchedule_detailList() {\n" +
                   "      return acd.getSchedule().getDetailList().getUnmodifiableList();\n" +
                   "    }\n" +
                   "\n" +
                   "    public Time_sheet_exceptionList getTime_sheet_exceptionList() {\n" +
                   "      return acd.getTime_sheet().getTime_sheet_exceptionList().getUnmodifiableList();\n" +
                   "    }\n" +
                   "\n" +
                   "    public List<Time_sheet_output> getTsoListForPayPreview() {\n" +
                   "      return tsoListForPayPreview;\n" +
                   "    }\n" +
                   "\n" +
                   "    public Approval_eventList getValidManagerApproveEvents() {\n" +
                   "      return approval_eventList.getValidManagerApproveEvents();\n" +
                   "    }\n" +
                   "\n" +
                   "    public TimeSheetIdentifier getTimeSheetIdentifier() {\n" +
                   "      return timeSheetIdentifier;\n" +
                   "    }\n" +
                   "\n" +
                   "    public EmployeePeriodInfo getEmployeePeriodInfo() {\n" +
                   "      return employeePeriodInfo;\n" +
                   "    }\n" +
                   "\n" +
                   "    public Employee_master getEmployeeMaster() {\n" +
                   "      return new Employee_master(acd.getEmployee_master());\n" +
                   "    }\n" +
                   "\n" +
                   "    public Employee getEmployee() {\n" +
                   "      return new Employee(acd.getEmployee_master().getEmployeeAsOfOrAfter(getPPEnd()));\n" +
                   "    }\n" +
                   "\n" +
                   "    public Asgnmt_master getAsgnmtMaster() {\n" +
                   "      return new Asgnmt_master(acd.getAsgnmtMaster());\n" +
                   "    }\n" +
                   "\n" +
                   "    public Asgnmt getAsgnmt() {\n" +
                   "      return new Asgnmt(acd.getAsgnmt());\n" +
                   "    }\n" +
                   "\n" +
                   "    public WDate getPPEnd() {\n" +
                   "      return employeePeriodInfo.getPp_end();\n" +
                   "    }\n" +
                   "\n" +
                   "    public WDate getPPBegin() {\n" +
                   "      return employeePeriodInfo.getPp_begin();\n" +
                   "    }\n" +
                   "\n" +
                   "    public Policy_profile getPolicy_profile() {\n" +
                   "      return policyProfile;\n" +
                   "    }\n" +
                   "\n" +
                   "\n" +
                   "    /**\n" +
                   "     * Return state information about the timesheet, which considers security\n" +
                   "     * relationship between the current state (locked/closed/amended) in relation to:\n" +
                   "     * <ul>\n" +
                   "     * <li>App_user Roles (app_user_right and right_grp tables)\n" +
                   "     * <li>Assignment effective dates\n" +
                   "     * <li>Employee record effective dates\n" +
                   "     * <li>System_feature read/write privileges\n" +
                   "     * <li>Approval level of the timesheet\n" +
                   "     * </ul>\n" +
                   "     *\n" +
                   "     * @return not ever null\n" +
                   "     */\n" +
                   "    public TimeSheetState getTimeSheetState() {\n" +
                   "      return timeSheetState;\n" +
                   "    }\n" +
                   "\n" +
                   "    public int getVersionNumber() {\n" +
                   "      return timeSheetIdentifier.getVersion();\n" +
                   "    }\n" +
                   "\n" +
                   "    public PayPeriodData getPayPeriodData() {\n" +
                   "      return acd.getPayPeriodData();\n" +
                   "    }\n" +
                   "\n" +
                   "    public boolean getIsNewlyUserAmended() {\n" +
                   "      return acd.isNewlyUserAmended();\n" +
                   "    }\n" +
                   "\n" +
                   "    public List<PayPeriodInfo> getPayPeriodList() {\n" +
                   "      return payPeriodList;\n" +
                   "    }\n" +
                   "\n" +
                   "    private static Collection<DbRecTime_sheet_output> createAdjustmentsPaidWithTimesheet(AllCalcDataManager acdm, AllCalculationData acd) {\n" +
                   "      try { return Collections.unmodifiableCollection(acdm.getAdjustmentsPaidWithTimesheet(acd)); }\n" +
                   "      catch (SQLException e) { throw new InternalApplicationException(\"Unexpected SQL Exception\", e); }\n" +
                   "    }\n" +
                   "\n" +
                   "    private void assertPayPeriodList() {\n" +
                   "      //TODO - probably need to show a \"nice\" message to the user saying that the employee doesn't\n" +
                   "      //TODO - have a viewable/editable pay period.\n" +
                   "      if(payPeriodList.isEmpty()) {\n" +
                   "        throw new InternalApplicationException(\"No viewable/editable pay periods found for employee \"\n" +
                   "                + employeePeriodInfo.getEmployeeMaster().getEmployee());\n" +
                   "      }\n" +
                   "    }\n" +
                   "\n" +
                   "    private static Set<WDate> createApprovedDays(AllCalculationData acd) {\n" +
                   "      // calculate days approved by daily approvals\n" +
                   "      Set<WDate> approvedDays = new HashSet<WDate>();\n" +
                   "      for( WDate date : acd.getPayPeriodData().getActiveDates().getAllDates() ) {\n" +
                   "        if( acd.isDayApproved(date) ) { approvedDays.add(date); }\n" +
                   "      }\n" +
                   "      return Collections.unmodifiableSet(approvedDays);\n" +
                   "    }\n" +
                   "\n" +
                   "    private final Collection<DbRecTime_sheet_output> adjustmentsPaidWithTimesheet;\n" +
                   "    private final Approval_eventList approval_eventList;\n" +
                   "    private final AssignmentPeriodState assignmentPeriodState;\n" +
                   "    private final List<BankBalanceResults> bankBalanceResults;\n" +
                   "    private final List<Time_sheet_output> tsoListForPayPreview;\n" +
                   "    private final TimeSheetIdentifier timeSheetIdentifier;\n" +
                   "    private final TimeSheetState timeSheetState;\n" +
                   "    private final EmployeePeriodInfo employeePeriodInfo;\n" +
                   "    private final ArrayList<PayPeriodInfo> payPeriodList;\n" +
                   "    private final Set<WDate> approvedDays;\n" +
                   "    private final Policy_profile policyProfile;\n" +
                   "    private final AllCalculationData acd;\n" +
                   "  }\n" +
                   "\n" +
                   "  AssignmentManager(GeneratedId assignmentId, Set<TimeSheetIdentifier> timeSheetIdentifiers, TimeEntryParmsPerAssignment parms) {\n" +
                   "    this.asgnmtMaster = getAggregateOrSingleAssignmentMaster(assignmentId);\n" +
                   "    setClassFields(parms, timeSheetIdentifiers);\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Obtains the single or aggregate assignment master for the given assignmentId.  If the given assignmentId is not a\n" +
                   "   * single or aggregate assignment, fetches the aggregate associated with the given assignmentId and returns that.\n" +
                   "   *\n" +
                   "   * @param assignmentId Id of the assigment to load.\n" +
                   "   * @return the single or aggregate assignment master for the given assignmentId.\n" +
                   "   */\n" +
                   "  private static Asgnmt_master getAggregateOrSingleAssignmentMaster(GeneratedId assignmentId) {\n" +
                   "    Asgnmt_master am = Asgnmt_master.load(assignmentId);\n" +
                   "    if(am.isAggregate() || am.isSingle()) {\n" +
                   "      return am;\n" +
                   "    }\n" +
                   "\n" +
                   "    //If it's not a single or aggregate, we need to load the aggregate and load that.\n" +
                   "    return Asgnmt_master.load(am.getAggregate_asgnmt());\n" +
                   "  }\n" +
                   "\n" +
                   "  AggregateTimeEntryTransactionResults load() {\n" +
                   "    return loadImpl();\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Reverts any changes that have not been committed to the database on this AssignmentManager\n" +
                   "   */\n" +
                   "  void revert() {\n" +
                   "    updatedAllCalcDataManager = null;\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * @return Return initial transaction results from originalAllCalcData, which must be set before calling this.\n" +
                   "   */\n" +
                   "  private AggregateTimeEntryTransactionResults loadImpl() {\n" +
                   "    AggregateTimeEntryTransactionResults aggregateResults = (AggregateTimeEntryTransactionResults) parms.getTimeEntryResultsFactory().newInstance();\n" +
                   "    for (TimeSheetIdentifier timeSheetId : getTimeSheetIdentifiers()) {\n" +
                   "      AllCalculationData acd = getOriginalAllCalcData(timeSheetId);\n" +
                   "      // Make sure system_record_id's are assigned - TimeEntryManager and client do not function correctly without\n" +
                   "      assignSystemRecordIds(acd);\n" +
                   "      CalcInfo calcInfo = new CalcInfo(getOriginalAllCalcDataManager(timeSheetId.getAsgnmt()), acd, timeSheetId, parms);\n" +
                   "      //todo-ute-nazim do we really need to perform TimeSheetDiff for the initial load ?\n" +
                   "      TimeEntryTransactionResults singleTimeSheetResults =\n" +
                   "              new TimeSheetDiff(timeSheetId, acd, calcInfo, TimeSheetCounters.getEmptyCounters(), NewToOldIdMap.EMPTY,\n" +
                   "                                Collections.EMPTY_LIST, new StatusMapsForTimesheetAndSchedule(), true, parms, null);\n" +
                   "      aggregateResults.add(singleTimeSheetResults);\n" +
                   "    }\n" +
                   "    return aggregateResults;\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Assign system_record_id's for time_sheet, time_sheet_detail's and time_sheet_exception's\n" +
                   "   * @param acd\n" +
                   "   */\n" +
                   "  private void assignSystemRecordIds(AllCalculationData acd) {\n" +
                   "    assignSystemRecordId(acd.getTime_sheet());\n" +
                   "    assignSystemRecordIds(acd.getTime_sheet().getTime_sheet_detailList());\n" +
                   "    assignSystemRecordIds(acd.getTime_sheet().getTime_sheet_exceptionList());\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * todo: store the event.getComment()\n" +
                   "   * @param event\n" +
                   "   */\n" +
                   "  AggregateTimeEntryTransactionResults amend(TransactionApprovalEvent event) {\n" +
                   "    TimeSheetIdentifier singleTimeSheetId = event.getTimeSheetIdentifier();\n" +
                   "    //TODO: Shouldn't this call createOriginalAllCalcDataManagerIfNeeded?  Seems like we needlessly reload the original\n" +
                   "    //TODO: ACDM.  This is because a timesheet which was prepared for amendment couldn't have been modified before it\n" +
                   "    //TODO: was amended.\n" +
                   "    invalidateAllCalcDataManagers();\n" +
                   "\n" +
                   "    // Create the amended ACD and put into the ACDM cache\n" +
                   "    EmployeePeriodInfo epInfo = null;\n" +
                   "    try {\n" +
                   "      AllCalcDataManager acdm = getOriginalAllCalcDataManager(singleTimeSheetId.getAsgnmt());\n" +
                   "      epInfo = acdm.getEmployeePeriodInfo(singleTimeSheetId.getPpEnd());\n" +
                   "      AllCalculationData amendedAllCalcData = acdm.createAmendedAllCalcData(epInfo, parms.getApp_user(), WDateTime.now());\n" +
                   "      // No need to hook this up to approval change logic, since it is an in-memory change so far.\n" +
                   "      //Recalculate the amended ACD object so that timesheet exceptions that are on the closed version (if any)\n" +
                   "      // get created on the amended timesheet as well\n" +
                   "      amendedAllCalcData.recalc(acdm);\n" +
                   "    }\n" +
                   "    catch (SQLException e) {\n" +
                   "      throw new InternalApplicationException(\"amending time sheet for \" + singleTimeSheetId, e);\n" +
                   "    }\n" +
                   "    catch (MultipleRowDbRecException e) {\n" +
                   "      throw new InternalApplicationException(\"amending time sheet for \" + singleTimeSheetId, e);\n" +
                   "    } catch (Exception e) {\n" +
                   "      throw new InternalApplicationException(\"amending time sheet for \" + singleTimeSheetId, e);\n" +
                   "    }\n" +
                   "    return loadImpl();\n" +
                   "  }\n" +
                   "\n" +
                   "  TimeSheetTransactionApplier applyTransaction(TimeSheetIdentifier id, TimeEntryTransaction trans) {\n" +
                   "    AllCalcDataManager updatedAcdm = getUpdatedAllCalcDataManager(id.getAsgnmt());\n" +
                   "    AllCalculationData acd = getAllCalcData(updatedAcdm, id);\n" +
                   "    return new TimeSheetTransactionApplier(trans, updatedAcdm, acd, parms, id);\n" +
                   "  }\n" +
                   "\n" +
                   "  private void recalc(TimeSheetIdentifier timeSheetId, Approval_event_type approvalEventType) {\n" +
                   "    AllCalcDataManager updatedAcdm = getUpdatedAllCalcDataManager(timeSheetId.getAsgnmt());\n" +
                   "    AllCalculationData acd = getAllCalcData(updatedAcdm, timeSheetId);\n" +
                   "    try {\n" +
                   "      if (approvalEventType == Approval_event_type.SAVE_SCHEDULE) {\n" +
                   "        // if we're saving a change to the schedule, the timesheet might need to be re-initialized.\n" +
                   "        acd.reinitTimeSheet(updatedAcdm, true);\n" +
                   "      }\n" +
                   "      acd.recalc(updatedAcdm);\n" +
                   "    }\n" +
                   "    catch (Exception e) {\n" +
                   "      throw new InternalApplicationException(\"calculate for \" + timeSheetId, e);\n" +
                   "    }\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Save one timesheet to a ListWriter using AllCalculationData.\n" +
                   "   * @param timeSheetId\n" +
                   "   * @param saveEvent\n" +
                   "   * @throws Exception\n" +
                   "   */\n" +
                   "  void save(TimeSheetIdentifier timeSheetId, Approval_event_type saveEvent) throws Exception {\n" +
                   "    GeneratedId asgnmtId = timeSheetId.getAsgnmt();\n" +
                   "    AllCalcDataManager acdMgr = getUpdatedAllCalcDataManager(asgnmtId);\n" +
                   "    AllCalculationData acd = getAllCalcData(acdMgr, timeSheetId);\n" +
                   "\n" +
                   "    // TODO: it'd be nice if we were able to retry a save if it failed because of unrelated changes (to accomodate for\n" +
                   "    // concurrent save operations on other component timesheets besides the one that the user is trying to save)\n" +
                   "    final ListWriter lw = new ListWriter();\n" +
                   "    acd.saveAfterApplyingApprovalEvent(acdMgr, saveEvent, lw);\n" +
                   "\n" +
                   "    //The exceptions must be generated after the ACD has performed approval related operations.\n" +
                   "    // Loading the original ACD to get the timesheet object before the approval. This will be used to determine\n" +
                   "    // timesheet's approval level before this save event.\n" +
                   "    AllCalculationData originalAcd = getAllCalcData(getOriginalAllCalcDataManager(asgnmtId), timeSheetId);\n" +
                   "    acdMgr.generateExceptionNotifications(acd, originalAcd.getTime_sheet(), acd.getTime_sheet(), lw);\n" +
                   "    lw.writeLists();\n" +
                   "\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * If time sheets were saved and written to the database successfully, update internal data\n" +
                   "   * to reflect what's in the database as the new \"original\" data.\n" +
                   "   */\n" +
                   "  void postSave() {\n" +
                   "    originalAllCalcDataManager = updatedAllCalcDataManager;\n" +
                   "    updatedAllCalcDataManager = null;\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Returns true if the given timesheet contains exceptions that should prevent save.\n" +
                   "   * @param timeSheetId time sheet ID\n" +
                   "   * @return true if exceptions exist that should prevent save, false otherwise.\n" +
                   "   */\n" +
                   "  boolean hasExceptionsPreventingSave(TimeSheetIdentifier timeSheetId) {\n" +
                   "    AllCalculationData acd = getUpdatedAllCalcData(timeSheetId);\n" +
                   "    try {\n" +
                   "      return acd.getTime_sheet().getTime_sheet_exceptionList().getDisallow_timesheet_save(acd.getAllPolicies());\n" +
                   "    }\n" +
                   "    catch (PolicyLookupRequiredException e) {\n" +
                   "      throw new InternalApplicationException(\"Policy error on timesheet \" + timeSheetId);\n" +
                   "    }\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Returns true if the given timesheet contains exceptions that should prevent submit.\n" +
                   "   * @param timeSheetId time sheet ID\n" +
                   "   * @return true if exceptions exist that should prevent submit, false otherwise.\n" +
                   "   */\n" +
                   "  boolean hasExceptionsPreventingSubmit(TimeSheetIdentifier timeSheetId) {\n" +
                   "    AllCalculationData acd = getUpdatedAllCalcData(timeSheetId);\n" +
                   "    try {\n" +
                   "      return acd.getTime_sheet().getTime_sheet_exceptionList().getDisallow_timesheet_submit(acd.getAllPolicies());\n" +
                   "    }\n" +
                   "    catch (PolicyLookupRequiredException e) {\n" +
                   "      throw new InternalApplicationException(\"Policy error on timesheet \" + timeSheetId);\n" +
                   "    }\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Compute {@link TimeEntryTransactionResults} changes to be returned in client.\n" +
                   "   * @param timeSheetId\n" +
                   "   * @param trans transaction which client just sent\n" +
                   "   * @param applyResults results (errors) of applying the transaction\n" +
                   "   * @param timeSheetSavedStatus a status to be added representing the save results - SAVED or ERROR, or null if none.\n" +
                   "   * @param transactionSuccessful value for {@link TimeEntryTransactionResults#getTransactionWasSuccessful}.\n" +
                   "   */\n" +
                   "  TimeSheetDiff getDiff(TimeSheetIdentifier timeSheetId, TimeEntryTransaction trans,\n" +
                   "                        TimeSheetTransactionApplier applyResults, TransactionStatus timeSheetSavedStatus,\n" +
                   "                        boolean transactionSuccessful)\n" +
                   "  {\n" +
                   "    AllCalcDataManager acdm = getLatestAllCalcDataManager(timeSheetId.getAsgnmt());\n" +
                   "    AllCalculationData acd = getAllCalcData(acdm, timeSheetId);\n" +
                   "    NewToOldIdMap newToOldIdMap = applyResults.getNewToOldIdMap();\n" +
                   "    StatusMapsForTimesheetAndSchedule timeSheetRowStatusMap = applyResults.getTimeEntryRowErrorMap();\n" +
                   "\n" +
                   "    if (cat.isDebugEnabled()) {\n" +
                   "      ObjectDumper.debug(cat, \"TimeSheetDiff getDiff before\", timeSheetRowStatusMap, null, true);\n" +
                   "    }\n" +
                   "\n" +
                   "    List<TransactionStatus> timeSheetStatuses = new ArrayList<TransactionStatus>(applyResults.getTimeSheetErrors());\n" +
                   "    TimeSheetCounters counters = new TimeSheetCounters(trans, timeSheetId, newToOldIdMap);\n" +
                   "    if (timeSheetSavedStatus != null) {\n" +
                   "      timeSheetStatuses.add(timeSheetSavedStatus);\n" +
                   "      if (timeSheetSavedStatus.getTransaction_status() == Transaction_status.SAVED ||\n" +
                   "              timeSheetSavedStatus.getTransaction_status() == Transaction_status.ERROR) {\n" +
                   "        // if we saved or there is an error mark every TimeEntryRow in the transaction as such.\n" +
                   "        // get all row id's from the transaction (new id's - not temp id's)\n" +
                   "        Collection transTimeSheetRowIds = applyResults.getTransactionTimeSheetRowIds();\n" +
                   "        Collection transScheduleRowIds = applyResults.getTransactionScheduleRowIds();\n" +
                   "        timeSheetRowStatusMap = new StatusMapsForTimesheetAndSchedule(transTimeSheetRowIds, transScheduleRowIds, timeSheetSavedStatus);\n" +
                   "        timeSheetRowStatusMap.addAll(applyResults.getTimeEntryRowErrorMap());\n" +
                   "      }\n" +
                   "    }\n" +
                   "\n" +
                   "    if (cat.isDebugEnabled()) {\n" +
                   "      ObjectDumper.debug(cat, \"TimeSheetDiff getDiff after\", timeSheetRowStatusMap, null, true);\n" +
                   "    }\n" +
                   "\n" +
                   "    CalcInfo calcInfo = new CalcInfo(acdm, acd, timeSheetId, parms);\n" +
                   "    return new TimeSheetDiff(timeSheetId, acd, calcInfo, counters, newToOldIdMap, timeSheetStatuses,\n" +
                   "                             timeSheetRowStatusMap, transactionSuccessful, parms, trans);\n" +
                   "  }\n" +
                   "\n" +
                   "  TimeSheetCalculationInfo getCalculationInfo(TimeSheetIdentifier timeSheetIdentifier) {\n" +
                   "    validate(timeSheetIdentifier);\n" +
                   "    final AllCalcDataManager acdm = getLatestAllCalcDataManager(timeSheetIdentifier.getAsgnmt());\n" +
                   "    return new CalcInfo(acdm, getAllCalcData(acdm, timeSheetIdentifier), timeSheetIdentifier, parms);\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Return the calculation info without auto-amending\n" +
                   "   * @param acdm\n" +
                   "   * @param timeSheetIdentifier\n" +
                   "   * @return\n" +
                   "   */\n" +
                   "  private TimeSheetCalculationInfo getCalculationInfo(AllCalcDataManager acdm, TimeSheetIdentifier timeSheetIdentifier) {\n" +
                   "    validate(timeSheetIdentifier);\n" +
                   "    try {\n" +
                   "      return new CalcInfo(acdm, acdm.getAllCalcData(timeSheetIdentifier.getPpEnd(), timeSheetIdentifier.getVersion()), timeSheetIdentifier, parms);\n" +
                   "    } catch (Exception e) {\n" +
                   "      throw new InternalApplicationException(\"Error creating CalcInfo \", e); \n" +
                   "    }\n" +
                   "  }\n" +
                   "\n" +
                   "  private void invalidateAllCalcDataManagers() {\n" +
                   "    originalAllCalcDataManager = null;\n" +
                   "    updatedAllCalcDataManager = null;\n" +
                   "  }\n" +
                   "\n" +
                   "  private void createUpdatedAllCalcDataManager(Set<TimeSheetIdentifier> timeSheetIds) {\n" +
                   "    AllCalcDataManager origAcdMgr = getOriginalAllCalcDataManager(asgnmtMaster.getAsgnmt());\n" +
                   "    try {\n" +
                   "      WDateList dateList = new WDateList();\n" +
                   "      for(TimeSheetIdentifier id : timeSheetIds) {\n" +
                   "        dateList.add(id.getPpEnd());\n" +
                   "      }\n" +
                   "      updatedAllCalcDataManager = origAcdMgr.copyForUpdate(dateList);\n" +
                   "    } catch (Exception e) {\n" +
                   "      throw new InternalApplicationException(\"Cloning for \" + timeSheetIds, e);\n" +
                   "    }\n" +
                   "  }\n" +
                   "\n" +
                   "  private void validate(TimeSheetIdentifier timeSheetId) throws IllegalArgumentException {\n" +
                   "    if (!timeSheetIdentifiers.contains(timeSheetId)) {\n" +
                   "      throw new IllegalArgumentException(\"Invalid time sheet specified: \" + timeSheetId\n" +
                   "        + \". not in \" + timeSheetIdentifiers);\n" +
                   "    }\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Obtains the original AllCalcDataManager for the given assignment Id.  The assignment id must be given because in a\n" +
                   "   * multiple assignment environment, a specific component needs to be requested, as originalAllCalcDataManager refers\n" +
                   "   * to the aggregate assignment.\n" +
                   "   *\n" +
                   "   * @param asgnmtId The id of the assignment to load.\n" +
                   "   * @return the original AllCalcDataManager for the given assignment Id\n" +
                   "   */\n" +
                   "  private AllCalcDataManager getOriginalAllCalcDataManager(GeneratedId asgnmtId) {\n" +
                   "    if(originalAllCalcDataManager == null) {\n" +
                   "      originalAllCalcDataManager = createOriginalAllCalcDataManager();\n" +
                   "      updatedAllCalcDataManager = null;\n" +
                   "    }\n" +
                   "\n" +
                   "    if(asgnmtId.equals(originalAllCalcDataManager.getAsgnmtId())) {\n" +
                   "      return originalAllCalcDataManager;\n" +
                   "    }\n" +
                   "\n" +
                   "    return originalAllCalcDataManager.getAllCalcDataManager(asgnmtId);\n" +
                   "  }\n" +
                   "\n" +
                   "  private AllCalcDataManager createOriginalAllCalcDataManager() {\n" +
                   "    AllPolicies allPolicies = PolicyManager.getInstance().getAllPolicies();\n" +
                   "    try {\n" +
                   "      return AllCalcDataManager.createForAsgnmt(asgnmtMaster, allPolicies);\n" +
                   "    }\n" +
                   "    catch (Exception e) {\n" +
                   "      throw new InternalApplicationException(\"Failed to load ACDM for assignment with id:\"+asgnmtMaster.getAsgnmt(), e);\n" +
                   "    }\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Obtains the updated AllCalcDataManager for the given assignment Id.  The assignment id must be given because in a\n" +
                   "   * multiple assignment environment, a specific component needs to be requested, as originalAllCalcDataManager refers\n" +
                   "   * to the aggregate assignment.\n" +
                   "   *\n" +
                   "   * @param asgnmtId The id of the assignment to load.\n" +
                   "   * @return the updated AllCalcDataManager for the given assignment Id\n" +
                   "   */\n" +
                   "  private AllCalcDataManager getUpdatedAllCalcDataManager(GeneratedId asgnmtId) {\n" +
                   "    if(updatedAllCalcDataManager == null) {\n" +
                   "      throw new InternalApplicationException(\"Attempted to use the updated all calc data manager before it was created.\");\n" +
                   "    }\n" +
                   "\n" +
                   "    if(asgnmtId.equals(updatedAllCalcDataManager.getAsgnmtId())) {\n" +
                   "      return updatedAllCalcDataManager;\n" +
                   "    }\n" +
                   "\n" +
                   "    return updatedAllCalcDataManager.getAllCalcDataManager(asgnmtId);\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Obtains the updated AllCalcDataManager for the given assignment Id, if present.  If not present, returns the\n" +
                   "   * original ACDM instead.  The assignment id must be given because in a\n" +
                   "   * multiple assignment environment, a specific component needs to be requested, as originalAllCalcDataManager refers\n" +
                   "   * to the aggregate assignment.\n" +
                   "   *\n" +
                   "   * @param asgnmtId The id of the assignment to load.\n" +
                   "   * @return  he updated AllCalcDataManager for the given assignment Id, if present.  If not present, returns the\n" +
                   "   * original ACDM instead.\n" +
                   "   */\n" +
                   "  AllCalcDataManager getLatestAllCalcDataManager(GeneratedId asgnmtId) {\n" +
                   "    if (updatedAllCalcDataManager != null) {\n" +
                   "      return getUpdatedAllCalcDataManager(asgnmtId);\n" +
                   "    }\n" +
                   "\n" +
                   "    return getOriginalAllCalcDataManager(asgnmtId);\n" +
                   "  }\n" +
                   "\n" +
                   "  //todo-ute: the method name is misleading as it may also create a new amended ACD if period is amendable by system on user's behalf\n" +
                   "  private AllCalculationData getAllCalcData(AllCalcDataManager mgr, TimeSheetIdentifier id) {\n" +
                   "    try {\n" +
                   "      EmployeePeriodInfo epInfo = mgr.getEmployeePeriodInfo(id.getPpEnd());\n" +
                   "      if (epInfo == null) {\n" +
                   "        final PolicyID asgnmtPolicyProfile = mgr.getAsgnmtMaster().getPolicy_profile(id.getPpEnd());\n" +
                   "        final PolicyID policyProfileId = (asgnmtPolicyProfile == null) ? PolicyID.EMPTY_POLICYID : asgnmtPolicyProfile;\n" +
                   "        throw new InvalidPayPeriodException(\"EmployeePeriodInfo could not be obtained for employee \" +\n" +
                   "            id.getEmployee() + \" and assignment \" + id.getAsgnmt() + \" and period end - \" + id.getPpEnd() + \". \" +\n" +
                   "            \"Possibly because the employee is not active on that date or there are not enough initialized policy profile \" +\n" +
                   "            \"periods for policy profile \" + policyProfileId );\n" +
                   "      }\n" +
                   "\n" +
                   "      TimeSheetCalculationInfo calcInfo = this.getCalculationInfo(mgr, id);\n" +
                   "      AssignmentPeriodState asgnmtPeriodState = calcInfo.getAssignmentPeriodState();\n" +
                   "      TimeSheetState timesheetState = calcInfo.getTimeSheetState();\n" +
                   "      //If requesting EDIT_VERSION, check to see if one exists or that period is amendable by the logged in user\n" +
                   "      if (id.getVersion() == EmployeePeriodVersionInfo.EDIT_VERSION && !epInfo.hasEditVersion()\n" +
                   "          && !isPeriodAmendable(epInfo, mgr, asgnmtPeriodState, timesheetState) ) {\n" +
                   "        throw new InternalApplicationException(\"An editable timesheet could not be obtained for employee \" +\n" +
                   "            id.getEmployee() + \" and period end - \" + id.getPpEnd());\n" +
                   "      }\n" +
                   "\n" +
                   "      //Check to see if an editable ACD is in ACD manager's cache - if one exists, use it\n" +
                   "      //An empty unmodifiable ACD for a prior period might be in cache if an ACD for prior timesheet is requested and\n" +
                   "      //if that's the case, we want to still be able to create an amended ACD\n" +
                   "      final AllCalculationData acd = mgr.getAllCalcData(id.getPpEnd(),id.getVersion(),AllCalculationData.FETCH_CACHED_ONLY);\n" +
                   "      if (acd != null && !acd.isUnmodifiable()) {\n" +
                   "        return acd;\n" +
                   "      }\n" +
                   "\n" +
                   "      //Create an amended ACD only if the period is amendable and the logged in user can amend it\n" +
                   "      if (isAmendableBySystemForUser(epInfo, mgr, asgnmtPeriodState)) {\n" +
                   "        final AllCalculationData amendedAllCalcData = mgr.createAmendedAllCalcData(epInfo, parms.getApp_user(), WDateTime.now());\n" +
                   "        // No need to hook this up to approval change logic, since it is an in-memory change so far.\n" +
                   "        //Recalculate the amended ACD so that timesheet exceptions (if any) get created on the amended timesheet\n" +
                   "        amendedAllCalcData.recalc(mgr);\n" +
                   "      }\n" +
                   "\n" +
                   "      //By this time, the ACD mgr's ACD cache will have the amended ACD if one's created in this method\n" +
                   "      //and we can retrieve the cached amended ACD or the ACD from the database\n" +
                   "      return mgr.getAllCalcData(id.getPpEnd(), id.getVersion());\n" +
                   "    } catch(InvalidPayPeriodException e) {\n" +
                   "      //Rethrow InvalidPeriodExceptions--don't wrap them.  We want them to be typed as InvalidPeriodExceptions.\n" +
                   "      throw e;\n" +
                   "    } catch (Exception e) {\n" +
                   "      throw new InternalApplicationException(\"Timesheet could not be obtained for employee \" +\n" +
                   "            id.getEmployee() + \", assignment \" + id.getAsgnmt()+ \", period end \" + id.getPpEnd() +  \" and version \" + id.getVersion() + \".\", e);\n" +
                   "    }\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * A period is amendable if one of the following is true:\n" +
                   "   * <pre>\n" +
                   "   * - {@link #isAmendableBySystemForUser(EmployeePeriodInfo, AllCalcDataManager, AssignmentPeriodState)\n" +
                   "   *    amendable by system on users's behalf}\n" +
                   "   * - {@link TimeSheetState#isAmendable() amendable directly by user} generally by clicking \"Amend\" button on\n" +
                   "   *   prior active period with one or more closed timesheets\n" +
                   "   * </pre>\n" +
                   "   */\n" +
                   "  private static boolean isPeriodAmendable(EmployeePeriodInfo epInfo, AllCalcDataManager mgr,\n" +
                   "                                           AssignmentPeriodState asgnmtPeriodState, TimeSheetState timesheetState)\n" +
                   "          throws SQLException, MultipleRowDbRecException {\n" +
                   "    return timesheetState.isAmendable() || isAmendableBySystemForUser(epInfo, mgr, asgnmtPeriodState);\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * A period is amendable by system on user's behalf (not the same as AUTO_AMEND or SYS_AMEND which are also created by\n" +
                   "   * system) if user has amend rights for the specified period as of today and if specified period is a priod period\n" +
                   "   * with no timesheet or it's a prior period timesheet with just system amended but not user amended\n" +
                   "   * @param epInfo\n" +
                   "   * @param mgr\n" +
                   "   * @param asgnmtPeriodState\n" +
                   "   * @return\n" +
                   "   * @throws PolicyLookupException\n" +
                   "   * @throws MultipleRowDbRecException\n" +
                   "   * @throws SQLException\n" +
                   "   * @see AssignmentPeriodState#isPeriodAmendable() \n" +
                   "   * @see Approval_event_type#SYS_AMEND\n" +
                   "   * @see Approval_event_type#AMEND\n" +
                   "   */\n" +
                   "  private static boolean isAmendableBySystemForUser(EmployeePeriodInfo epInfo, AllCalcDataManager mgr,\n" +
                   "                                                    AssignmentPeriodState asgnmtPeriodState)\n" +
                   "      throws MultipleRowDbRecException, SQLException {\n" +
                   "    return asgnmtPeriodState.isPeriodAmendable() &&  // TODO: this doesn't match the javadoc?\n" +
                   "        ( mgr.isPriorModifiablePeriodWithNoTimesheet(epInfo) || epInfo.isPriorPeriodWithSysAmendVersionOnly() );\n" +
                   "  }\n" +
                   "\n" +
                   "  private AllCalculationData getOriginalAllCalcData(TimeSheetIdentifier timeSheetIdentifier) {\n" +
                   "    return getAllCalcData(getOriginalAllCalcDataManager(timeSheetIdentifier.getAsgnmt()), timeSheetIdentifier);\n" +
                   "  }\n" +
                   "\n" +
                   "  private AllCalculationData getUpdatedAllCalcData(TimeSheetIdentifier timeSheetIdentifier) {\n" +
                   "    return getAllCalcData(getUpdatedAllCalcDataManager(timeSheetIdentifier.getAsgnmt()), timeSheetIdentifier);\n" +
                   "  }\n" +
                   "\n" +
                   "\n" +
                   "  private Set<TimeSheetIdentifier> getTimeSheetIdentifiers() {\n" +
                   "    return timeSheetIdentifiers;\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Returns the pay period end for the period containing date provided, relative\n" +
                   "   * to the defined periods for this assignment.\n" +
                   "   *\n" +
                   "   * @param aDateInUnknownPeriod any date we want to know the period for\n" +
                   "   * @return never null\n" +
                   "   */\n" +
                   "  WDate getPpEndForDate(GeneratedId asgnmtId, WDate aDateInUnknownPeriod) {\n" +
                   "    return TimeSchedUtils.getPpEndForDate(getOriginalAllCalcDataManager(asgnmtId), aDateInUnknownPeriod);\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Assign system_record_id's to any DbRec objecs in the collection which lack them.\n" +
                   "   * @param dbRecList List of DbRec objects on which to check the system_record_id.  Records in list will be modified.\n" +
                   "   */\n" +
                   "  static private void assignSystemRecordIds(ListWrapBase dbRecList) {\n" +
                   "    for (Iterator iterator = dbRecList.getCollection().iterator(); iterator.hasNext();) {\n" +
                   "      DbRec dbRec = (DbRec) iterator.next();\n" +
                   "      assignSystemRecordId(dbRec);\n" +
                   "    }\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Assign system_record_id to a DbRec object, if it is not set or contains a temporary id.\n" +
                   "   * @param dbRec DbRec objects on which to check/change the system_record_id.\n" +
                   "   */\n" +
                   "  static private void assignSystemRecordId(DbRec dbRec) {\n" +
                   "    if (dbRec.getSystem_record_id() == null || dbRec.getSystem_record_id().requiresPermanentId()) {\n" +
                   "      dbRec.setSystem_record_id(SystemId.getNewID());\n" +
                   "    }\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Removes the last approval event (employee approval) from approval event list from the AllCalculationData object of\n" +
                   "   * {@link #updatedAllCalcDataManager}\n" +
                   "   * @param timeSheetId  - Used to get the {@link AllCalculationData} object from {@link #updatedAllCalcDataManager}\n" +
                   "   */\n" +
                   "  void undoEmployeeApproval(TimeSheetIdentifier timeSheetId) {\n" +
                   "    final AllCalculationData updatedAllCalcData = getUpdatedAllCalcData(timeSheetId);\n" +
                   "    updatedAllCalcData.getApproval_eventList().removeLastApprovalEvent();\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Update the current object to be able to be used for other timesheets for this assignment\n" +
                   "   * @param parms\n" +
                   "   * @param timeSheetIdentifiers\n" +
                   "   */\n" +
                   "  public void update(TimeEntryParmsPerAssignment parms, Set<TimeSheetIdentifier> timeSheetIdentifiers) {\n" +
                   "    setClassFields(parms, timeSheetIdentifiers);\n" +
                   "  }\n" +
                   "\n" +
                   "  private void setClassFields(TimeEntryParmsPerAssignment parms, Set<TimeSheetIdentifier> timeSheetIdentifiers) {\n" +
                   "    this.parms = parms;\n" +
                   "    this.timeSheetIdentifiers = TimeEntryParmsPerAssignment.TIMESHEET_IDENTIFIER_TREESET_FACTORY.newInstance();\n" +
                   "    this.timeSheetIdentifiers.addAll(timeSheetIdentifiers);\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Recalculates and Saves timesheets (if saveData == true) for timesheet ids in {@link #parms} that belong to this\n" +
                   "   * assignment\n" +
                   "   *\n" +
                   "   * @param trans\n" +
                   "   * @return AggregateTimeEntryTransactionResults for all saved timesheets\n" +
                   "   * @throws Exception in case of fatal error during timesheet recalc & save e.g. errors while store data in DB etc.\n" +
                   "   * This precludes ConcurrentUserModification exception. Any exception thrown from this method, eventually gets\n" +
                   "   * escalated as transaction exception\n" +
                   "   */\n" +
                   "  public TimeEntryTransactionResults recalcAndSaveTimesheets(TimeEntryTransaction trans, boolean saveData) throws Exception {\n" +
                   "    // TODO-13285: Remove this code and implement merging concurrently modified time sheets\n" +
                   "    AllCalcDataManager tempAcdMgr = null;\n" +
                   "    AggregateTimeEntryTransactionResults results = (AggregateTimeEntryTransactionResults) parms.getTimeEntryResultsFactory().newInstance();\n" +
                   "    Set<TimeSheetIdentifier> timeSheetIds = getAffectedTimeSheetIdentifiers(trans, parms);\n" +
                   "    int retryCount = 0;\n" +
                   "    boolean canRetryConcurrentModification = true;\n" +
                   "    if (timeSheetIds.isEmpty()) {\n" +
                   "      return results;\n" +
                   "    }\n" +
                   "\n" +
                   "    assert timeSheetIdentifiers.containsAll(timeSheetIds) : \"Attempted to recalculate a timesheet identifier not managed by this AssignmentManager.\";\n" +
                   "    createUpdatedAllCalcDataManager(timeSheetIds);\n" +
                   "\n" +
                   "    TransactionStatus timeSheetSaveStatus = null;\n" +
                   "    Map<TimeSheetIdentifier, TimeSheetTransactionApplier> applyResultsMap = new HashMap<TimeSheetIdentifier, TimeSheetTransactionApplier>();\n" +
                   "    for (TimeSheetIdentifier timeSheetId : timeSheetIds) {\n" +
                   "      TimeSheetTransactionApplier applyResults = applyTransaction(timeSheetId, trans);\n" +
                   "      if(applyResults.getApprovalEventType() != Approval_event_type.SAVE_SCHEDULE) {\n" +
                   "        applyResultsMap.put(timeSheetId, applyResults);\n" +
                   "      }\n" +
                   "\n" +
                   "      recalc(timeSheetId, applyResults.getApprovalEventType());\n" +
                   "\n" +
                   "      boolean isWithdrawal =\n" +
                   "              (trans.findTransactionApprovalEvent(timeSheetId, Approval_event_type.WITHDRAWAL) != null);\n" +
                   "      if (!isWithdrawal) {\n" +
                   "        // There are 3 conditions identified in the following if statement that can prevent an \"action\" on the\n" +
                   "        // current timesheet. The first is an exception that prevents save. In this case, we should never allow a\n" +
                   "        // save of the timesheet to occur. The second condition is an exception that prevents submit. In this\n" +
                   "        // condition, we should only allow a save if the user is not attempting to submit (apply an APPROVAL event).\n" +
                   "        // The third case is errors that occur while applying transactions to the timesheet. An example of when this\n" +
                   "        // may occur is if the same time sheet detail row is modified by 2 users. In all of these cases we prevent\n" +
                   "        // the save operation from executing.\n" +
                   "        // In the next condition where we check if an error has occurred, we remove the last approval event from the\n" +
                   "        // timesheet if we did not save AND it was a submit (APPROVAL) event. Transactions only allow 1 event to be applied\n" +
                   "        // to the timesheet. The transaction explicitly excludes SAVE_TIME_SHEET and SAVE_SCHEDULE events from being applied.\n" +
                   "        // It relies on these events being applied only by the save operation itself. As a result, a maximum of 2 events may\n" +
                   "        // be added to the timesheet as a result of the transaction (the event on the transaction if it was not a save event)\n" +
                   "        // and the SAVE_TIME_SHEET or SAVE_SCHEDULE event added during the save operation.\n" +
                   "\n" +
                   "        if (hasSubmitEvent(trans, timeSheetId) &&\n" +
                   "                hasExceptionsPreventingSubmit(timeSheetId)) {\n" +
                   "          timeSheetSaveStatus = getHighestPriorityError(timeSheetSaveStatus, Messages.EXCEPTIONS_PREVENT_SUBMIT);\n" +
                   "        }\n" +
                   "\n" +
                   "        if (hasExceptionsPreventingSave(timeSheetId)) {\n" +
                   "          timeSheetSaveStatus = new TransactionStatusImpl(Transaction_status.ERROR,\n" +
                   "                  Messages.EXCEPTIONS_PREVENT_SAVE.getLabel());\n" +
                   "        }\n" +
                   "      }\n" +
                   "\n" +
                   "      if (applyResults.hasErrors()) {\n" +
                   "        timeSheetSaveStatus = new TransactionStatusImpl(Transaction_status.ERROR,\n" +
                   "                  Messages.TIME_SHEET_EXCEPTION_JAVA_EXCEPTION .getLabel());\n" +
                   "      }\n" +
                   "    }\n" +
                   "\n" +
                   "\n" +
                   "    boolean errorPreventsAction = (timeSheetSaveStatus != null);\n" +
                   "\n" +
                   "\n" +
                   "    Approval_event lastOriginalApprovalEvent = null;\n" +
                   "    for (TimeSheetIdentifier timeSheetId : timeSheetIds) {\n" +
                   "      TimeSheetTransactionApplier applyResults = applyResultsMap.get(timeSheetId);\n" +
                   "      try {\n" +
                   "        while (saveData && canRetryConcurrentModification) {\n" +
                   "          if (errorPreventsAction) {\n" +
                   "            // If the changes were not saved due to errors, and this operation (transaction) was an employee timesheet submission,\n" +
                   "            // remove the approval event from the unsaved ACD.\n" +
                   "            // NOTE: We do not have to concern ourselves with removing SAVE_TIME_SHEET or SAVE_SCHEDULE events here as these events\n" +
                   "            // are explicitly EXCLUDED in the TimeSheetTransactionApplier (not applied) and are applied by the save operation (which\n" +
                   "            // has not executed if we made it here.\n" +
                   "            if (hasSubmitEvent(trans, timeSheetId)) {\n" +
                   "              undoEmployeeApproval(timeSheetId);\n" +
                   "            }\n" +
                   "          } else {\n" +
                   "            save(timeSheetId, applyResults.getApprovalEventType());\n" +
                   "            timeSheetSaveStatus = new TransactionStatusImpl(Transaction_status.SAVED,\n" +
                   "                    Messages.TIME_SHEET_SAVED.getLabel());\n" +
                   "            postSave();\n" +
                   "          }\n" +
                   "          // All operations completed, so retries are no longer necessary\n" +
                   "          canRetryConcurrentModification = false;\n" +
                   "        }\n" +
                   "      } catch (ConcurrentUserModificationException ex) {\n" +
                   "        // Store the old AcdMgr, when a concurrent mod error due to another user modifying a time sheet occurs we want\n" +
                   "        // to receive the same new approval events to ensure that the user is forced to reload the time sheet to continue.\n" +
                   "        // TODO-13285: Remove the tempAcdMgr and make merging concurrently modified time sheets possible,\n" +
                   "        // TODO-13285: requires considering multiple edge cases for the user interface\n" +
                   "        if (tempAcdMgr == null) {\n" +
                   "          tempAcdMgr = getOriginalAllCalcDataManager(asgnmtMaster.getAsgnmt());\n" +
                   "        }\n" +
                   "\n" +
                   "        if (retryCount == MAX_RETRIES) {\n" +
                   "          // return the acdMgr to its previous state, this ensures the user must reload the time sheet\n" +
                   "          // TODO-13285: Remove this code and implement merging concurrently modified time sheets\n" +
                   "          originalAllCalcDataManager = tempAcdMgr;\n" +
                   "          GeneratedId errorId = ServerErrorLogger.singleton.log(new ServerError(\"Exception saving time sheet\", ex,\n" +
                   "                  Program_source.SERVER_REQUEST, parms.getApp_user().getLogin_id()));\n" +
                   "          cat.error(\"Unable to save time sheet.  debug_error_log id:\" + errorId, ex);\n" +
                   "          //Use timeSheetSaveStatus to send concurrent error message to user.\n" +
                   "          errorPreventsAction = true;\n" +
                   "          timeSheetSaveStatus = new TransactionStatusImpl(Transaction_status.ERROR,\n" +
                   "                  Messages.TIME_SHEET_EXCEPTION_CONCURRENT_USER.getLabel());\n" +
                   "          // We have retried saving and have hit the MAX_RETRIES threshold, so stop retrying.\n" +
                   "          canRetryConcurrentModification = false;\n" +
                   "        } else {\n" +
                   "          // On the first retry, we need to capture the last (most recent) approval event that existed on this timesheet prior to applying\n" +
                   "          // any transactions because when we reload the \"original\" ACD to attempt to reapply the transactions we will lose track\n" +
                   "          // of this (we'll pull in approval events for transactions that were saved in other sessions/processes). This is necessary to\n" +
                   "          // allow more than 1 retry\n" +
                   "          if (lastOriginalApprovalEvent == null) {\n" +
                   "            lastOriginalApprovalEvent = getAllCalcData(getOriginalAllCalcDataManager(timeSheetId.getAsgnmt()), timeSheetId).getApproval_eventList().getLastApprovalEvent();\n" +
                   "          }\n" +
                   "          retryCount++;\n" +
                   "          // attempt to reapply the transaction\n" +
                   "          try {\n" +
                   "            applyResults = attemptTimeSheetTransactionReapply(trans, timeSheetId, lastOriginalApprovalEvent);\n" +
                   "          } catch (ConcurrentUserModificationException cume) {\n" +
                   "            // return the acdMgr to its previous state, this ensures the user must reload the time sheet\n" +
                   "            // TODO-13285: Remove this code and implement merging concurrently modified time sheets\n" +
                   "            originalAllCalcDataManager = tempAcdMgr;\n" +
                   "            // if we were unable to reapply transactions, no further retries are necessary (because it will just keep failing)\n" +
                   "            // subsequent retries only succeed if we were able to reapply the new transactions successfully and still received\n" +
                   "            // another ConcurrentUserModificationException (a second change occurred while applying transactions)\n" +
                   "            canRetryConcurrentModification = false;\n" +
                   "            errorPreventsAction = true;\n" +
                   "            timeSheetSaveStatus = new TransactionStatusImpl(Transaction_status.ERROR,\n" +
                   "                    Messages.TIME_SHEET_EXCEPTION_CONCURRENT_USER.getLabel());\n" +
                   "          }\n" +
                   "        }\n" +
                   "      }\n" +
                   "      results.add(getDiff(timeSheetId, trans, applyResults, timeSheetSaveStatus, !errorPreventsAction));\n" +
                   "    }\n" +
                   "    return results;\n" +
                   "  }\n" +
                   "\n" +
                   "  ";

    @NonNls String part2 = "\n" +
                   "  private TransactionStatus getHighestPriorityError(TransactionStatus existingStatus, Message newError) {\n" +
                   "    if(existingStatus == null) {\n" +
                   "      return newError;\n" +
                   "    }\n" +
                   "    TransactionStatus timeSheetSaveStatus = new TransactionStatusImpl(Transaction_status.ERROR,\n" +
                   "            );\n" +
                   "    return timeSheetSaveStatus;\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Returns true if the given TimeEntryTransaction has a submit event for the given time sheet.\n" +
                   "   * @param trans TimeEntryTransaction to search\n" +
                   "   * @param timeSheetId time sheet to search\n" +
                   "   * @return true if a submit event exists\n" +
                   "   */\n" +
                   "  private static boolean hasSubmitEvent(TimeEntryTransaction trans, TimeSheetIdentifier timeSheetId) {\n" +
                   "    return trans.findTransactionApprovalEvent(timeSheetId, Approval_event_type.APPROVAL) != null;\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Resolves modifications that impact only non-\"input\" values on the timesheet to prevent them\n" +
                   "   * from causing ConcurrentUserModificationExceptions on subsequent attempts at saving the data\n" +
                   "   * @param trans time entry transaction to resolve\n" +
                   "   * @param id time sheet ID\n" +
                   "   * @param lastApprovalEvent the most recent approval event on the timesheet prior to any transactions\n" +
                   "   * @return TimeSheetTransactionApplier if transactions were reapplied\n" +
                   "   * @throws Exception on error loading the acd\n" +
                   "   */\n" +
                   "  private TimeSheetTransactionApplier attemptTimeSheetTransactionReapply(final TimeEntryTransaction trans, final TimeSheetIdentifier id, Approval_event lastApprovalEvent) throws Exception {\n" +
                   "    // Clean out the ACDM cache to force reloads of the data from the database\n" +
                   "    invalidateAllCalcDataManagers();\n" +
                   "    AllCalculationData originalAcd = getAllCalcData(getOriginalAllCalcDataManager(id.getAsgnmt()), id);\n" +
                   "    // Get any new approval events that exist in the database\n" +
                   "    Approval_eventList newApprovalEvents = originalAcd.getApproval_eventList().getApprovalsSince(lastApprovalEvent);\n" +
                   "    // Check if a \"concurrent save\" is possible given the events that have occurred\n" +
                   "    if (newApprovalEvents.canConcurrentSave()) {\n" +
                   "      //TODO:FIX\n" +
                   "      //createUpdatedAllCalcDataManager(listOfAllIdsHere);\n" +
                   "      TimeSheetTransactionApplier applyResults = applyTransaction(id, trans); // reapply transactions\n" +
                   "      recalc(id, applyResults.getApprovalEventType()); // recalculate\n" +
                   "      DbRecTime_sheet originalTimeSheet = originalAcd.getTime_sheet();\n" +
                   "      DbRecTime_sheet updatedTimeSheet = getAllCalcData(getUpdatedAllCalcDataManager(id.getAsgnmt()), id).getTime_sheet();\n" +
                   "      // Copy the system fields from the original time sheet to the updated time sheet because\n" +
                   "      // we have determined at this point that the concurrent save is okay based on the difference\n" +
                   "      // between these 2 time sheets. Copying these fields over will allow the save to complete\n" +
                   "      // without causing a ConcurrentUserModificationException by making the update counter match.\n" +
                   "      DbRecFieldCopier copier = new DbRecFieldCopier(DataDictionary.getCltnOfSystemFieldNames());\n" +
                   "      copier.copyFields(originalTimeSheet, updatedTimeSheet);\n" +
                   "      return applyResults;\n" +
                   "    } else {\n" +
                   "      // Otherwise, throw the exception\n" +
                   "      throw new ConcurrentUserModificationException(\"Unable to save time sheet for assignment \" + originalAcd.getAsgnmtId() + \"due to concurrent changes.\");\n" +
                   "    }\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Returns a set of AssignmentManagers that are affected by changes in the given transaction and managed by this\n" +
                   "   * TimeEntryManager.\n" +
                   "   *\n" +
                   "   * @return a set of AssignmentManagers that are affected by changes in the given transaction and managed by this\n" +
                   "   * TimeEntryManager.\n" +
                   "   */\n" +
                   "  public Set<TimeSheetIdentifier> getAffectedTimeSheetIdentifiers(TimeEntryTransaction transaction, TimeEntryParmsPerAssignment parms) {\n" +
                   "    Set<TimeSheetIdentifier> affectedTimeSheetIds = new HashSet<TimeSheetIdentifier>();\n" +
                   "    for(GeneratedId assignmentId : getAssignmentIds()) {\n" +
                   "      for(TimeSheetIdentifier timeSheetId : parms.getTimeSheetIdentifiersForAssignment(assignmentId)) {\n" +
                   "        if(!transaction.obtainAllRows(timeSheetId).isEmpty()) {\n" +
                   "          affectedTimeSheetIds.add(timeSheetId);\n" +
                   "        }\n" +
                   "      }\n" +
                   "    }\n" +
                   "    return affectedTimeSheetIds;\n" +
                   "  }\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Obtains all assignment id's that are managed by this AssignmentManager.\n" +
                   "   *\n" +
                   "   * @return all assignment id's that are managed by this AssignmentManager.\n" +
                   "   */\n" +
                   "  public Set<GeneratedId> getAssignmentIds() {\n" +
                   "    Set<GeneratedId> assignmentIds = new HashSet<GeneratedId>();\n" +
                   "    Asgnmt_masterList amList = asgnmtMaster.getCompAsgnmtMasters();\n" +
                   "    for(Asgnmt_master am : amList) {\n" +
                   "      assignmentIds.add(am.getAsgnmt());\n" +
                   "    }\n" +
                   "    return assignmentIds;\n" +
                   "  }\n" +
                   "\n" +
                   "  public GeneratedId getEmployee() {\n" +
                   "    return asgnmtMaster.getEmployee();\n" +
                   "  }\n" +
                   "\n" +
                   "  /** Single or aggregate assignment master associated with this manager */\n" +
                   "  private final Asgnmt_master asgnmtMaster;\n" +
                   "\n" +
                   "  private TimeEntryParmsPerAssignment parms;\n" +
                   "\n" +
                   "  /** Set of {@link TimeSheetIdentifier}'s which are managed by this object.\n" +
                   "   * Composed of all the identifiers in the TimeEntryParms for this assignment and related components.\n" +
                   "   */\n" +
                   "  private Set<TimeSheetIdentifier> timeSheetIdentifiers;\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Original Single or Aggregate ACDM, as loaded from the database.  ACD's in here are not modified.\n" +
                   "   * This ACDM is lazily loaded, and should always be obtained from {@link #getOriginalAllCalcDataManager(GeneratedId)}.\n" +
                   "   *\n" +
                   "   * Null means that the ACDM has not yet been loaded from the database, and any attempts to use it should first\n" +
                   "   * initialize it.  Note:  The meaning of null is different than updatedAllCalcDataManager's null meaning.\n" +
                   "   */\n" +
                   "  private AllCalcDataManager originalAllCalcDataManager = null;\n" +
                   "\n" +
                   "  /**\n" +
                   "   * Updated Single or Aggregate ACDM--a shallow copy of originalAllCalcDataManager.  ACD's in here are modified by\n" +
                   "   * TimeEntryTransactions.  This ACDM is NOT lazily loaded--null means no changes present, and non-null means the\n" +
                   "   * updated ACDM has been explicitly created.\n" +
                   "   *\n" +
                   "   * Null means that no changes have been made to the ACDM or data, and any attempts to use it should either throw a\n" +
                   "   * descriptive error, or explicitly create the updatedAllCalcDataManager.\n" +
                   "   * Note:  The meaning of null is different than originalAllCalcDataManager's null meaning.\n" +
                   "   */\n" +
                   "  private AllCalcDataManager updatedAllCalcDataManager = null;\n" +
                   "\n" +
                   "  private static final int MAX_RETRIES = 2;\n" +
                   "\n" +
                   "  private static final Category cat=Category.getInstance(AssignmentManager.class.getName());\n" +
                   "}";
    configureFromFileText("Foo.java", part1 + part2);

    final PsiDocumentManager docManager = PsiDocumentManager.getInstance(ourProject);
    final Document doc = docManager.getDocument(myFile);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> doc.insertString(part1.length(), "/**"));


    boolean old = DebugUtil.CHECK;
    DebugUtil.CHECK = true;
    try {
      docManager.commitAllDocuments();
    }
    finally {
      DebugUtil.CHECK = old;
    }
  }
}
