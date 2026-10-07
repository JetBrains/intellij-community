# Update Feedback Survey

**Title:** How did your update go?
**Description:** You recently updated <product name> from the new Update button. Help us make updating smoother.

**1. How easy was it to update <product name>?** *(segmented button, 1–5)*
Hints: Very difficult · · Very easy

**2. The Update button in the header is…** *(combobox)*
- Easy to miss
- Just right
- A bit distracting
- Too distracting

Hint: "Just a couple words about the new button". This checks the main risk of making it more visible, which is that it starts to feel nagging.

**3. Did you run into any of these during the update?** *(checkboxes + Other)*
- Download was slow or failed
- Update failed and I had to reinstall manually
- Some plugins were disabled or incompatible
- Settings, keymap or UI layout changed unexpectedly
- Restart or re-indexing took too long
- It wasn't clear what would happen or when to restart
- No problems
- Other: ___

**4. What would have made updating better?** *(textarea, optional)*

**Rollout notes:**

- **Timing:** Show the survey in the new version on idle.
  
- **Targeting:** Only show it to users who started the update from the header button or the gear menu. That way the answers are about the new funnel and not the Toolbox App or manual installs. You can identify those use by `ide.update.toolbar.widget.clicked` key with `true` in `PropertiesComponent.getInstance()` storage.