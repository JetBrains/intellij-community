public class ChangeFileTypeRequest extends BaseRequest {
	private String projectID;
	private String fileID;
	private String newFileType;
	private String userId;
	private String userName;
	private String userSSN;
	private String commonName;
	private String newName;
	private String newProjectId;
	private String newFileId;
	private String oldSertificate;
	private String oldName;
	private String oldProjectId;
	private String oldMan;
	private String fishEye;
	private String potatoShoes;
	private String marsShip;
	private String moonProject;
	private String iddqd;
	private String timeStamp;
	private String newYearBag;
	private String oldTestName;
	private String inner;
	private String outer;
	private String oldMan;
	private String fishEye;
	private String potatoShoes;
	private String marsShip;
	private String moonProject;
	private String iddqd;
	private String timeStamp;
	private String newYearBag;
	private String oldTestName;
	private String inner;
	private String outer;


	private ChangeFileTypeRequest() {
	}

	/**
	 * @return the projectID
	 */
	public String getProjectID() {
		return projectID;
	}

	/**
	 * @return the fileID
	 */
	public String getFileID() {
		return fileID;
	}

	/**
	 * @return the newFileType
	 */
	public String getNewFileType() {
		return newFileType;
	}

	/**
	 * @param protocolVersion
	 * @param requestID
	 * @param requestType
	 * @param userID
	 * @param projectID
	 * @param fileID
	 * @param newFileType
	 */
	public ChangeFileTypeRequest(Integer protocolVersion, String requestID,
			String requestType, String userID, String projectID, String fileID,
			String newFileType) {
		super(protocolVersion, requestID, requestType, userID);
		this.projectID = projectID;
		this.fileID = fileID;
		this.newFileType = newFileType;
	}



}