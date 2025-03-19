TIPS & TRICKS:
	1.	In Applet#init(), call Microba.init() to handle browser refresh button correctly.
	
CONTRIBUTORS:
(random order)
	* Alessandro Falappa: Italian translation
	* Frido van Orden, Henk van Voorthuijsen: Dutch translation
	* Gustavo Santucho: Spanish translation
	* Gregory Kaczmarczyk: Polish translation
	* Philipp Meier: German translation
	* Claus Nielsen: Danish translation
	* Felix Bordea: Romanian translation
	* David Ekholm: Swedish translation

TRANSLATION:
	For the list of supported language translations, look into 
	com\michaelbaranov\microba\calendar\DefaultCalendarResources.properties
	
	Help translating needed! Please contribute!
	Send the translations to: michael[.]baranov[@]gmail[.]com	

KNOWN PROBLEMS:

	1.	DatePicker: while editing the field, enter arbitrary string after the last formatted character,
	position cursor anywhere past the last formatted character, press up or down key -> exception is 
	raised:
	
	java.lang.IllegalArgumentException: Invalid index
	...
	
	Reason: a bug in Sun's javax.swing.text.InternationalFormatter. 
	
	2.	DatePicker: set the style to STYLE_MODERN, open dropdown, click month combo box -> dropdown 
	is hidden OR can not close dropdown anymore OR exception (depends on JRE version).
	
	Reason: a bug in Sun's javax.swing.JPopupMenu, when using "popup in popup".
	
	3.	DatePicker: if a heavy-weight popup is used to display dropdown (for ex. it goes outside a JFrame),
	dropdown does not receive keyboard focus anymore.
	
	Reason: a bug in Sun's focus handling routines.