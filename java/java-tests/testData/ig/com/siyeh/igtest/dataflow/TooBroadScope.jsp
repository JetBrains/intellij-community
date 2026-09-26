<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page isELIgnored="false" %>
<%@ taglib prefix="core" uri="http://java.sun.com/jsp/jstl/core" %>
<html>
	<head><title>Title</title></head>
	<body>
		<% String s = "value"; %>
		<core:out value="<%=s.substring(1)%>"/> <br/>
	</body>
</html>